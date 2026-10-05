package com.scanku.app.ui.nav

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.scanku.app.ui.camera.CameraScreen
import com.scanku.app.ui.document.DocumentScreen
import com.scanku.app.ui.home.HomeScreen
import com.scanku.app.ui.page.PageScreen
import com.scanku.app.ui.review.ReviewScreen
import com.scanku.app.ui.settings.SettingsScreen

/** Route table. -1 means "no document yet" (a new one is created on first save). */
object Routes {
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val CAMERA = "camera?docId={docId}"
    const val REVIEW = "review?docId={docId}&capture={capture}"
    const val DOCUMENT = "document/{docId}"
    const val PAGE = "page/{pageId}"

    fun camera(docId: Long?) = "camera?docId=${docId ?: NO_DOC}"
    fun review(docId: Long?, capture: String) = "review?docId=${docId ?: NO_DOC}&capture=${Uri.encode(capture)}"
    fun document(docId: Long) = "document/$docId"
    fun page(pageId: Long) = "page/$pageId"

    const val NO_DOC = -1L
}

private fun Long.orNull(): Long? = takeIf { it != Routes.NO_DOC }

@Composable
fun AppNavHost(nav: NavHostController = rememberNavController()) {
    NavHost(navController = nav, startDestination = Routes.HOME) {

        composable(Routes.HOME) {
            HomeScreen(
                onScan = { nav.navigate(Routes.camera(null)) },
                onImported = { capture -> nav.navigate(Routes.review(null, capture)) },
                onOpenDocument = { nav.navigate(Routes.document(it)) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            Routes.CAMERA,
            arguments = listOf(navArgument("docId") { type = NavType.LongType; defaultValue = Routes.NO_DOC }),
        ) { entry ->
            val docId = entry.arguments?.getLong("docId")?.orNull()
            CameraScreen(
                documentId = docId,
                onCaptured = { capture -> nav.navigate(Routes.review(docId, capture)) },
                onClose = { nav.popBackStack() },
            )
        }

        composable(
            Routes.REVIEW,
            arguments = listOf(
                navArgument("docId") { type = NavType.LongType; defaultValue = Routes.NO_DOC },
                navArgument("capture") { type = NavType.StringType; defaultValue = "" },
            ),
        ) { entry ->
            val docId = entry.arguments?.getLong("docId")?.orNull()
            val capture = entry.arguments?.getString("capture").orEmpty()
            ReviewScreen(
                documentId = docId,
                captureName = capture,
                onBack = { nav.popBackStack() },
                onSavedScanMore = { savedDocId ->
                    nav.navigate(Routes.camera(savedDocId)) {
                        popUpTo(Routes.HOME)
                    }
                },
                onSavedDone = { savedDocId ->
                    nav.navigate(Routes.document(savedDocId)) {
                        popUpTo(Routes.HOME)
                    }
                },
            )
        }

        composable(
            Routes.DOCUMENT,
            arguments = listOf(navArgument("docId") { type = NavType.LongType }),
        ) { entry ->
            val docId = entry.arguments?.getLong("docId") ?: Routes.NO_DOC
            DocumentScreen(
                documentId = docId,
                onBack = { nav.popBackStack() },
                onAddPage = { nav.navigate(Routes.camera(docId)) },
                onImported = { capture -> nav.navigate(Routes.review(docId, capture)) },
                onOpenPage = { nav.navigate(Routes.page(it)) },
            )
        }

        composable(
            Routes.PAGE,
            arguments = listOf(navArgument("pageId") { type = NavType.LongType }),
        ) { entry ->
            PageScreen(
                pageId = entry.arguments?.getLong("pageId") ?: Routes.NO_DOC,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { nav.popBackStack() })
        }
    }
}
