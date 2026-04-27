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

package com.ollitert.llm.server.ui.benchmark

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalculateValueSeriesTest {

  @Test
  fun emptyListReturnsDefault() {
    val result = calculateValueSeries(emptyList())
    assertEquals(0, result.valueCount)
    assertEquals(0.0, result.min, 0.0)
    assertEquals(0.0, result.max, 0.0)
    assertEquals(0.0, result.avg, 0.0)
  }

  @Test
  fun singleValue() {
    val result = calculateValueSeries(listOf(42.0))
    assertEquals(1, result.valueCount)
    assertEquals(42.0, result.min, 0.0)
    assertEquals(42.0, result.max, 0.0)
    assertEquals(42.0, result.avg, 0.0)
    assertEquals(42.0, result.medium, 0.0)
    assertEquals(42.0, result.pct25, 0.0)
    assertEquals(42.0, result.pct75, 0.0)
  }

  @Test
  fun twoValues() {
    val result = calculateValueSeries(listOf(10.0, 20.0))
    assertEquals(10.0, result.min, 0.0)
    assertEquals(20.0, result.max, 0.0)
    assertEquals(15.0, result.avg, 0.001)
    assertEquals(15.0, result.medium, 0.001)
  }

  @Test
  fun oddNumberOfValues() {
    val result = calculateValueSeries(listOf(1.0, 3.0, 5.0, 7.0, 9.0))
    assertEquals(1.0, result.min, 0.0)
    assertEquals(9.0, result.max, 0.0)
    assertEquals(5.0, result.avg, 0.001)
    assertEquals(5.0, result.medium, 0.001)
  }

  @Test
  fun evenNumberOfValues() {
    val result = calculateValueSeries(listOf(1.0, 2.0, 3.0, 4.0))
    assertEquals(1.0, result.min, 0.0)
    assertEquals(4.0, result.max, 0.0)
    assertEquals(2.5, result.avg, 0.001)
    assertEquals(2.5, result.medium, 0.001)
  }

  @Test
  fun preservesOriginalOrder() {
    val input = listOf(5.0, 1.0, 3.0)
    val result = calculateValueSeries(input)
    assertEquals(listOf(5.0, 1.0, 3.0), result.valueList)
  }

  @Test
  fun percentileInterpolation() {
    val result = calculateValueSeries(listOf(10.0, 20.0, 30.0, 40.0))
    assertEquals(17.5, result.pct25, 0.001)
    assertEquals(32.5, result.pct75, 0.001)
  }

  @Test
  fun identicalValues() {
    val result = calculateValueSeries(listOf(7.0, 7.0, 7.0))
    assertEquals(7.0, result.min, 0.0)
    assertEquals(7.0, result.max, 0.0)
    assertEquals(7.0, result.avg, 0.0)
    assertEquals(7.0, result.medium, 0.0)
  }

  @Test
  fun unsortedInputProducesCorrectStats() {
    val result = calculateValueSeries(listOf(50.0, 10.0, 30.0, 20.0, 40.0))
    assertEquals(10.0, result.min, 0.0)
    assertEquals(50.0, result.max, 0.0)
    assertEquals(30.0, result.avg, 0.001)
    assertEquals(30.0, result.medium, 0.001)
  }

  @Test
  fun largeDataset() {
    val values = (1..100).map { it.toDouble() }
    val result = calculateValueSeries(values)
    assertEquals(1.0, result.min, 0.0)
    assertEquals(100.0, result.max, 0.0)
    assertEquals(50.5, result.avg, 0.001)
    assertEquals(50.5, result.medium, 0.001)
    assertTrue(result.pct25 in 25.0..26.0)
    assertTrue(result.pct75 in 75.0..76.0)
    assertEquals(100, result.valueCount)
  }
}
