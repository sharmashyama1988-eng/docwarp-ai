package com.docwarp.scanner.ui.navigation

import android.app.Application
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.docwarp.scanner.DocWarpApplication
import com.docwarp.scanner.ui.camera.CameraScannerScreen
import com.docwarp.scanner.ui.camera.CameraScannerViewModel
import com.docwarp.scanner.ui.editor.FilterStudioScreen
import com.docwarp.scanner.ui.editor.FilterStudioViewModel
import com.docwarp.scanner.ui.editor.ManualCropScreen
import com.docwarp.scanner.ui.editor.ManualCropViewModel
import com.docwarp.scanner.ui.export.PdfExportScreen
import com.docwarp.scanner.ui.export.PdfExportViewModel
import com.docwarp.scanner.ui.gallery.DocumentGalleryScreen
import com.docwarp.scanner.ui.gallery.DocumentGalleryViewModel

/**
 * Central Navigation Compose Host routing between all 6 screens of DocWarp AI
 */
@Composable
fun DocWarpNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val app = context.applicationContext as DocWarpApplication
    val repository = app.documentScanRepository
    val pipelineWorker = app.documentPipelineWorker

    NavHost(
        navController = navController,
        startDestination = Screen.Camera.route,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(220)) },
        exitTransition = { fadeOut(animationSpec = tween(220)) }
    ) {
        // Screen 1: Camera Scanner Viewfinder
        composable(Screen.Camera.route) {
            val vm = viewModel {
                CameraScannerViewModel(app, repository, pipelineWorker)
            }
            CameraScannerScreen(
                viewModel = vm,
                onNavigateToGallery = {
                    navController.navigate(Screen.Gallery.route)
                }
            )
        }

        // Screen 3: Multi-Page Gallery & Reorder Sheet
        composable(Screen.Gallery.route) {
            val vm = viewModel {
                DocumentGalleryViewModel(app, repository, pipelineWorker)
            }
            DocumentGalleryScreen(
                viewModel = vm,
                onNavigateBackToCamera = {
                    navController.popBackStack()
                },
                onNavigateToCrop = { pageId ->
                    navController.navigate(Screen.ManualCrop.createRoute(pageId))
                },
                onNavigateToFilter = { pageId ->
                    navController.navigate(Screen.FilterStudio.createRoute(pageId))
                },
                onNavigateToExport = {
                    navController.navigate(Screen.PdfExport.route)
                }
            )
        }

        // Screen 4: Precision Manual Crop & Corner Fine-Tuning
        composable(
            route = Screen.ManualCrop.route,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""
            val vm = viewModel {
                ManualCropViewModel(app, repository, pageId)
            }
            ManualCropScreen(
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Screen 5: Filter Studio
        composable(
            route = Screen.FilterStudio.route,
            arguments = listOf(navArgument("pageId") { type = NavType.StringType })
        ) { backStackEntry ->
            val pageId = backStackEntry.arguments?.getString("pageId") ?: ""
            val vm = viewModel {
                FilterStudioViewModel(app, repository, pageId)
            }
            FilterStudioScreen(
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        // Screen 6: PDF Preview, Metadata & Compression Sheet
        composable(Screen.PdfExport.route) {
            val vm = viewModel {
                PdfExportViewModel(app, repository)
            }
            PdfExportScreen(
                viewModel = vm,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
