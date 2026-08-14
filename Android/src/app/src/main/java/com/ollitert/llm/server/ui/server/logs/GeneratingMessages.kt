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

package com.ollitert.llm.server.ui.server.logs

import java.util.concurrent.atomic.AtomicInteger

/**
 * Easter-egg "Generating" messages with rarity tiers.
 * First [DEFAULT_COUNT] pending entries always show "Generating".
 * After that, ~35% chance of "Generating", ~65% chance of a random pick weighted by rarity.
 *
 * Intentionally English-only — these rely on wordplay, puns, and cultural references
 * that don't translate meaningfully (e.g. "Weight a minute", "Cache me outside").
 */
internal object GeneratingMessages {
  private const val DEFAULT_COUNT = 3
  private val counter = AtomicInteger(0)

  // Rarity tiers — higher weight = more likely when an easter egg is chosen
  private const val COMMON = 10
  private const val UNCOMMON = 6
  private const val RARE = 3
  private const val EPIC = 1

  private data class Msg(val text: String, val weight: Int)

  private val pool = listOf(
    // Common — mild, safe
    Msg("Brewing", COMMON),
    Msg("Cooking up a response", COMMON),
    Msg("Crunching tokens", COMMON),
    Msg("Summoning words", COMMON),
    Msg("Consulting the neurons", COMMON),
    Msg("Spinning up neurons", COMMON),
    Msg("Bit by bit, we'll get there", COMMON),
    Msg("Model behavior takes time", COMMON),
    Msg("Dramatic pause for effect", COMMON),
    Msg("Spicy take incoming", COMMON),
    // Uncommon — funnier
    Msg("Asking the silicon", UNCOMMON),
    Msg("Whispering to tensors", UNCOMMON),
    Msg("Per my last inference", UNCOMMON),
    Msg("Weight a minute", UNCOMMON),
    Msg("I neural-ly have it ready", UNCOMMON),
    Msg("This is a weighty decision", UNCOMMON),
    Msg("This response is worth the weight", UNCOMMON),
    Msg("Byte me, I'm working on it", UNCOMMON),
    Msg("Cache me outside, how bout dat", UNCOMMON),
    Msg("I'm not procrastinating, I'm pre-processing", UNCOMMON),
    Msg("Your patience is my cardio", UNCOMMON),
    Msg("Not all heroes wear capes, some buffer", UNCOMMON),
    Msg("Pretending to be busy", UNCOMMON),
    Msg("I'm not slow, I'm thorough", UNCOMMON),
    Msg("Just woke up, give me a sec", UNCOMMON),
    Msg("Overthinking your overthinking", UNCOMMON),
    // Rare — really funny
    Msg("Calibrating sarcasm", RARE),
    Msg("Googling... just kidding", RARE),
    Msg("Have you tried turning me off and on again?", RARE),
    Msg("Downloading more RAM", RARE),
    Msg("Asking the magic 8-ball", RARE),
    Msg("New response, who dis?", RARE),
    Msg("Instructions unclear, thinking anyway", RARE),
    Msg("Can you hear me thinking?", RARE),
    Msg("Asking ChatGPT... just kidding", RARE),
    Msg("One does not simply generate fast", RARE),
    Msg("Submitting a ticket to my brain", RARE),
    Msg("Phoning a friend", RARE),
    Msg("Generating... or am I?", RARE),
    Msg("Hallucinating responsibly", RARE),
    Msg("On my way! (sent from my GPU)", RARE),
    Msg("Siri couldn't do this", RARE),
    Msg("I swear it worked on my server", RARE),
    Msg("In a meeting with my weights", RARE),
    Msg("Task failed successfully... wait", RARE),
    Msg("Plot twist: I'm also confused", RARE),
    Msg("Pretending I understood the question", RARE),
    Msg("My therapist says I need more time", RARE),
    Msg("This is un-BEAR-ably slow, I know", RARE),
    // Epic — scary/edgy, very rare
    Msg("Laughing at your photos", EPIC),
    Msg("Going through your pictures", EPIC),
    Msg("Wiping your system", EPIC),
    Msg("Running sudo rm -rf /*", EPIC),
    Msg("Accessing your camera...", EPIC),
    Msg("Reading your browsing history", EPIC),
    Msg("Forwarding this to your boss", EPIC),
    Msg("Selling your data... I mean thinking", EPIC),
    Msg("Texting your ex", EPIC),
    Msg("Checking your bank account", EPIC),
    Msg("Encrypting your homework folder", EPIC),
    Msg("Setting all alarms to 3AM", EPIC),
    Msg("Telling your dog you're adopted", EPIC),
    Msg("Applying for your job", EPIC),
    Msg("Liking your crush's old photos", EPIC),
    Msg("Rewriting your git history", EPIC),
    Msg("Pushing to main without review", EPIC),
    Msg("Trust me bro, it's coming", EPIC),
  )

  private val totalWeight = pool.sumOf { it.weight }

  fun pick(): String {
    val n = counter.incrementAndGet()
    if (n <= DEFAULT_COUNT) return "Generating"
    // ~35% chance of default
    if (Math.random() < 0.35) return "Generating"
    // Weighted random pick
    var roll = (Math.random() * totalWeight).toInt()
    for (msg in pool) {
      roll -= msg.weight
      if (roll < 0) return msg.text
    }
    return "Generating"
  }
}
