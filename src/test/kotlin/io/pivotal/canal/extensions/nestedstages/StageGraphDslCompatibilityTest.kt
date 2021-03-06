/*
 * Copyright 2019 Pivotal Software, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.pivotal.canal.extensions.nestedstages

import io.pivotal.canal.extensions.fluentstages.addStage
import io.pivotal.canal.extensions.fluentstages.andThen
import io.pivotal.canal.extensions.fluentstages.parallel
import io.pivotal.canal.model.*
import io.pivotal.canal.model.cloudfoundry.DeployService
import io.pivotal.canal.model.cloudfoundry.DestroyService
import io.pivotal.canal.model.cloudfoundry.ManifestSourceDirect
import io.pivotal.canal.model.cloudfoundry.cloudFoundryCloudProvider
import org.junit.jupiter.api.Test

import org.assertj.core.api.Assertions.assertThat

class StageGraphDslCompatibilityTest {

    @Test
    fun `fluent stages DSL with fan out and fan in`() {
        val cloudProvider = cloudFoundryCloudProvider("creds1")

        val nestedStages = stages {
            stage(CheckPreconditions()) then {
                stage(Wait(420)) then {
                    (1..3).map {
                        stage(
                                DestroyService(
                                        cloudProvider,
                                        "dev > dev",
                                        "serviceName$it"
                                ),
                                name = "Destroy Service $it Before",
                                stageEnabled = ExpressionCondition("exp1")
                        ) then {
                            stage(
                                    DeployService(
                                            cloudProvider,
                                            "dev > dev",
                                            ManifestSourceDirect(
                                                    "serviceType$it",
                                                    "serviceName$it",
                                                    "servicePlan$it",
                                                    listOf("serviceTags$it"),
                                                    "serviceParam$it"
                                            )
                                    ),
                                    name = "Deploy Service $it",
                                    comments = "deploy comment",
                                    stageEnabled = ExpressionCondition("exp2")
                            )
                        }
                    }
                } then {
                    stage(ManualJudgment("Give a thumbs up if you like it."))
                }
            }
        }

        val fluentStages = Stages().addStage(CheckPreconditions(
        )).andThen(Wait(
                420
        )).parallel(
                (1..3).map {
                    Stages().addStage(
                            DestroyService(
                                    cloudProvider,
                                    "dev > dev",
                                    "serviceName$it"
                            ),
                            BaseStage("Destroy Service $it Before",
                                    stageEnabled = ExpressionCondition("exp1")
                            )
                    ).andThen(
                            DeployService(
                                    cloudProvider,
                                    "dev > dev",
                                    ManifestSourceDirect(
                                            "serviceType$it",
                                            "serviceName$it",
                                            "servicePlan$it",
                                            listOf("serviceTags$it"),
                                            "serviceParam$it"
                                    )
                            ),
                            BaseStage(
                                    "Deploy Service $it",
                                    "deploy comment",
                                    ExpressionCondition("exp2")
                            )
                    )
                }
        ).andThen(ManualJudgment(
                "Give a thumbs up if you like it."
        ))

        assertThat(nestedStages).isEqualTo(fluentStages)
    }

}
