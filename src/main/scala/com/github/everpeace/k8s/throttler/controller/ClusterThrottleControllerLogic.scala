/*
 * Copyright 2018 Shingo Omura <https://github.com/everpeace>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.everpeace.k8s.throttler.controller
import com.github.everpeace.k8s._
import com.github.everpeace.k8s.throttler.crd.v1alpha1
import com.github.everpeace.k8s.throttler.crd.v1alpha1.Implicits._
import com.github.everpeace.k8s.throttler.crd.v1alpha1.ResourceCount
import skuber.Resource.ResourceList
import skuber._

trait ClusterThrottleControllerLogic {

  def isClusterThrottleActiveFor(pod: Pod, clthrottle: v1alpha1.ClusterThrottle): Boolean =
    clthrottle.isActiveFor(pod)
  def isClusterThrottleInsufficientFor(pod: Pod, clthrottle: v1alpha1.ClusterThrottle): Boolean =
    clthrottle.isInsufficientFor(pod)
  def calcNextClusterThrottleStatuses(
      targetClusterThrottles: Set[v1alpha1.ClusterThrottle],
      podsInAllNamespaces: Set[Pod]
    ): List[(ObjectKey, v1alpha1.ClusterThrottle.Status)] = {

    for {
      clthrottle <- targetClusterThrottles.toList

      matchedPods = podsInAllNamespaces.filter(p =>
        clthrottle.spec.selector.matches(p.metadata.labels))
      running = matchedPods.filter(p => p.status.exists(_.phase.exists(_ == Pod.Phase.Running)))
      usedResource = v1alpha1.ResourceAmount(
        resourceCounts = for {
          rc <- clthrottle.spec.threshold.resourceCounts
          _  <- rc.pod
        } yield ResourceCount(pod = Option(running.size)),
        resourceRequests = {
          val actual =
            running.toList.map(_.totalRequests).foldLeft(Map.empty: ResourceList)(_ add _)
          val ret = for {
            (r, _) <- clthrottle.spec.threshold.resourceRequests if actual.contains(r)
          } yield r -> actual(r)
          ret
        }
      )
      nextStatus = clthrottle.spec.statusFor(usedResource)

      toUpdate <- if (clthrottle.status != Option(nextStatus)) {
                   List(clthrottle.key -> nextStatus)
                 } else {
                   List.empty
                 }
    } yield toUpdate
  }
}