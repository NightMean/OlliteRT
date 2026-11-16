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

package com.ollitert.llm.server.data.allowlist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ModelScopeLinkTest {
  @Test fun parsesRepositoryAndPinnedFileLinks() {
    val revision = "a".repeat(40)
    assertEquals(ModelScopeLink("owner/model", site = ModelScopeSite.CHINA), parseModelScopeLink("https://modelscope.cn/models/owner/model/files"))
    assertEquals(ModelScopeLink("owner/model", "master", "model.litertlm", ModelScopeSite.CHINA),
      parseModelScopeLink("https://www.modelscope.cn/models/owner/model/file/view/master/model.litertlm"))
    assertEquals(ModelScopeLink("owner/model", revision, "folder/model file.litertlm", ModelScopeSite.CHINA),
      parseModelScopeLink("https://modelscope.cn/models/owner/model/resolve/$revision/folder/model%20file.litertlm"))
    assertEquals(ModelScopeLink("owner/model", site = ModelScopeSite.INTERNATIONAL),
      parseModelScopeLink("https://www.modelscope.ai/models/owner/model"))
    assertEquals(ModelScopeLink("owner/model"), parseModelScopeLink("owner/model"))
    val fileUrl = modelScopeFileUrl("owner/model", revision, "folder/model file.litertlm", ModelScopeSite.INTERNATIONAL)
    assertEquals(ModelScopeLink("owner/model", revision, "folder/model file.litertlm", ModelScopeSite.INTERNATIONAL),
      parseModelScopeLink(fileUrl))
  }

  @Test fun rejectsUntrustedOrUnsafeLinks() {
    assertNull(parseModelScopeLink("https://modelscope.cn.evil.example/models/owner/model"))
    assertNull(parseModelScopeLink("https://user@modelscope.cn/models/owner/model"))
    assertNull(parseModelScopeLink("http://modelscope.cn/models/owner/model"))
    assertNull(parseModelScopeLink("https://modelscope.cn/models/owner/model/resolve/../model.litertlm"))
    assertNull(parseModelScopeLink("https://modelscope.cn/models/owner/model/resolve/${"a".repeat(40)}/../model.litertlm"))
    assertNull(parseModelScopeLink("https://modelscope.cn/api/v1/models/owner/model/repo?Revision=master&FilePath=../bad.litertlm"))
    assertNull(parseModelScopeLink("https://modelscope.cn/api/v1/models/owner/model/repo?Revision=master&Revision=other&FilePath=a.litertlm"))
    assertNull(parseModelScopeLink("https://modelscope.cn/api/v1/models/owner/model/repo?Revision=master&FilePath=a.litertlm&extra=x"))
    assertNull(parseModelScopeLink("https://modelscope.cn.example.test/api/v1/models/owner/model/repo?Revision=master&FilePath=a.litertlm"))
    val plus = modelScopeFileUrl("owner/model", "a".repeat(40), "nested/model+v1.litertlm")
    assertEquals(ModelScopeLink("owner/model", "a".repeat(40), "nested/model+v1.litertlm", ModelScopeSite.CHINA), parseModelScopeLink(plus))
  }
}
