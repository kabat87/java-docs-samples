/*
 * Copyright 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.cloudrun;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc
class VoteControllerTests {

  @Autowired private MockMvc mockMvc;

  @Test
  public void shouldBeSuccessful_getIndex() throws Exception {
    mockMvc
        .perform(get("/"))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("CATS v DOGS")));
  }

  @Test
  public void shouldBeUnsuccessful_postWithoutToken() throws Exception {
    mockMvc.perform(post("/")).andExpect(status().isUnauthorized());
  }

  @Test
  public void shouldBeUnsuccessful_postWithBadToken() throws Exception {
    try {
      mockMvc
          .perform(post("/").header("Authorization", "Bearer iam-a-token"))
          .andExpect(status().isForbidden());
    } catch (Exception e) {
      System.out.println("Caught FirebaseApp error");
    }
  }
}
