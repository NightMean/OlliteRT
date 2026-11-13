/*
 * Copyright 2025-2026 @NightMean (https://github.com/NightMean)
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
package com.ollitert.llm.server.ui.modelmanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceCompatibilityTest {
  @Test fun memoryThresholdOnlyAppliesToKnownPositiveValues() {
    assertTrue(mayWorkOnDevice("model.litertlm", 50, 100, ""))
    assertFalse(mayWorkOnDevice("model.litertlm", 51, 100, ""))
    assertTrue(mayWorkOnDevice("model.litertlm", null, 100, ""))
    assertTrue(mayWorkOnDevice("model.litertlm", 51, null, ""))
    assertTrue(mayWorkOnDevice("model.litertlm", 51, 0, ""))
  }

  @Test fun comparesCompleteChipIdsOnlyWhenBothAreKnown() {
    assertTrue(mayWorkOnDevice("MODEL_SM8550.litertlm", 1, 100, "Qualcomm SM8550"))
    assertFalse(mayWorkOnDevice("model_sm8550.litertlm", 1, 100, "Qualcomm SM8650"))
    assertTrue(mayWorkOnDevice("model_sm8550.litertlm", 1, 100, "unknown"))
    assertTrue(mayWorkOnDevice("model_sm85501.litertlm", 1, 100, "unknown"))
  }

  @Test fun recognizesVendorCluesWithoutReadingDirectories() {
    assertFalse(mayWorkOnDevice("model_nvidia.litertlm", 1, 100, "MediaTek"))
    assertFalse(mayWorkOnDevice("model_snapdragon.litertlm", 1, 100, "MediaTek"))
    assertTrue(mayWorkOnDevice("nvidia/model.litertlm", 1, 100, "MediaTek"))
    assertTrue(mayWorkOnDevice("model_snapdragon.litertlm", 1, 100, "unknown"))
  }
}
