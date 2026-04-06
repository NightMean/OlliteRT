package com.ollite.llm.server

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import com.ollite.llm.server.ui.modelmanager.ModelManagerViewModel

/**
 * Legacy alias — delegates to [OlliteApp].
 * Retained only for compatibility with commented-out preview references.
 */
@Composable
fun GalleryApp(
  navController: NavHostController = rememberNavController(),
  modelManagerViewModel: ModelManagerViewModel,
) {
  OlliteApp(modelManagerViewModel = modelManagerViewModel, navController = navController)
}
