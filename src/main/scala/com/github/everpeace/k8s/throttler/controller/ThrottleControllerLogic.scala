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
import com.github.everpeace.k8s.throttler.crd.v1alpha1.ResourceAmount
import com.github.everpeace.util.Injection._
import skuber._
import cats.instances.list._

trait ThrottleControllerLogic {
  def isThrottleAlreadyActiveFor(pod: Pod, throttle: v1alpha1.Throttle): Boolean =
    throttle.isAlreadyActiveFor(pod)

  def isThrottleInsufficientFor(pod: Pod, throttle: v1alpha1.Throttle): Boolean =
    throttle.isInsufficientFor(pod)

  def calcNextThrottleStatuses(
      targetThrottles: Set[v1alpha1.Throttle],
      podsInNs: Set[Pod],
      at: skuber.Timestamp
    ): List[(ObjectKey, v1alpha1.Throttle.Status)] = {

    for {
      throttle <- targetThrottles.toList

      matchedPods = podsInNs.filter(pod => throttle.spec.selector.matches(pod))
      runningPods = matchedPods
        .filter(p => p.status.exists(_.phase.exists(_ == Pod.Phase.Running)))
        .toList
      runningTotal = runningPods.==>[List[ResourceAmount]].foldLeft(zeroResourceAmount)(_ add _)
      nextStatus   = throttle.spec.statusFor(runningTotal, at)

      toUpdate <- if (throttle.status != Option(nextStatus)) {
                   List(throttle.key -> nextStatus)
                 } else {
                   List.empty
                 }
    } yield toUpdate
  }
}
