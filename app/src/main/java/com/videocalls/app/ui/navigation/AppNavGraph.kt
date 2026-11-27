package com.videocalls.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.videocalls.app.data.model.CallType
import com.videocalls.app.data.repository.AuthRepository
import com.videocalls.app.data.repository.CallSignalingRepository
import com.videocalls.app.data.repository.GroupRepository
import com.videocalls.app.ui.auth.LoginScreen
import com.videocalls.app.ui.auth.SimIdentityScreen
import com.videocalls.app.ui.call.GroupCallScreen
import com.videocalls.app.ui.call.IncomingCallScreen
import com.videocalls.app.ui.call.SingleCallScreen
import com.videocalls.app.ui.group.CreateGroupScreen
import com.videocalls.app.ui.group.GroupDetailScreen
import com.videocalls.app.ui.home.HomeScreen
import kotlinx.coroutines.launch

@Composable
fun AppNavGraph(
    navController: NavHostController,
    currentUid: String?,
    startDestination: String,
    signalingRepo: CallSignalingRepository = CallSignalingRepository(),
    groupRepo: GroupRepository = GroupRepository(),
    authRepo: AuthRepository = AuthRepository()
) {
    val coroutineScope = rememberCoroutineScope()
    fun getActiveUid(): String? = FirebaseAuth.getInstance().currentUser?.uid ?: currentUid

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.SimIdentity.route) {
            SimIdentityScreen(
                onIdentityBound = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.SimIdentity.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Login.route) {
            SimIdentityScreen(
                onIdentityBound = {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Home.route) {
            val uid = getActiveUid() ?: return@composable
            HomeScreen(
                currentUid = uid,
                onOpenSecretTestScreen = {
                    navController.navigate(
                        Screen.SingleCall.createRoute(
                            callId = "solo_test_${System.currentTimeMillis()}",
                            isOutgoing = true,
                            targetName = "Solo Test",
                            targetPhone = "",
                            isVideo = true,
                            isSoloTest = true
                        )
                    )
                },
                onStartSingleCall = { targetUid, targetName, targetPhone, callType ->
                    coroutineScope.launch {
                        val myName = authRepo.getLocalDisplayName().ifBlank { "User" }
                        val myPhone = authRepo.getLocalPhoneNumber()
                        val callId = signalingRepo.createSingleCall(
                            callerId = uid,
                            callerName = myName,
                            callerPhone = myPhone,
                            receiverId = targetUid,
                            receiverPhone = targetPhone,
                            callType = callType
                        )
                        navController.navigate(
                            Screen.SingleCall.createRoute(
                                callId = callId,
                                isOutgoing = true,
                                targetName = targetName,
                                targetPhone = targetPhone,
                                isVideo = callType == CallType.VIDEO
                            )
                        )
                    }
                },
                onOpenGroupDetail = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId))
                },
                onStartGroupCall = { groupId, groupName, isVideo ->
                    coroutineScope.launch {
                        val myName = authRepo.getLocalDisplayName().ifBlank { "User" }
                        val myPhone = authRepo.getLocalPhoneNumber()
                        val callId = signalingRepo.createGroupCall(
                            callerId = uid,
                            callerName = myName,
                            callerPhone = myPhone,
                            groupId = groupId,
                            callType = if (isVideo) CallType.VIDEO else CallType.AUDIO
                        )
                        navController.navigate(
                            Screen.GroupCall.createRoute(
                                callId = callId,
                                groupId = groupId,
                                groupName = groupName,
                                isVideo = isVideo
                            )
                        )
                    }
                },
                onJoinActiveCall = { callId, groupId, groupName ->
                    navController.navigate(
                        Screen.GroupCall.createRoute(
                            callId = callId,
                            groupId = groupId,
                            groupName = groupName,
                            isVideo = true
                        )
                    )
                },
                onCreateGroupClick = {
                    navController.navigate(Screen.CreateGroup.route)
                },
                onSignOut = {
                    navController.navigate(Screen.SimIdentity.route) {
                        popUpTo(Screen.Home.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.CreateGroup.route) {
            val uid = getActiveUid() ?: return@composable
            CreateGroupScreen(
                currentUid = uid,
                onNavigateBack = { navController.popBackStack() },
                onGroupCreated = { groupId ->
                    navController.navigate(Screen.GroupDetail.createRoute(groupId)) {
                        popUpTo(Screen.CreateGroup.route) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Screen.GroupDetail.route,
            arguments = listOf(navArgument("groupId") { type = NavType.StringType })
        ) { backStackEntry ->
            val uid = getActiveUid() ?: return@composable
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            GroupDetailScreen(
                groupId = groupId,
                currentUid = uid,
                onNavigateBack = { navController.popBackStack() },
                onStartGroupCall = { gId, gName, isVideo ->
                    coroutineScope.launch {
                        val myName = authRepo.getLocalDisplayName().ifBlank { "User" }
                        val myPhone = authRepo.getLocalPhoneNumber()
                        val callId = signalingRepo.createGroupCall(
                            callerId = uid,
                            callerName = myName,
                            callerPhone = myPhone,
                            groupId = gId,
                            callType = if (isVideo) CallType.VIDEO else CallType.AUDIO
                        )
                        navController.navigate(
                            Screen.GroupCall.createRoute(callId, gId, gName, isVideo)
                        )
                    }
                },
                onJoinActiveCall = { callId, gId, gName ->
                    navController.navigate(
                        Screen.GroupCall.createRoute(callId, gId, gName, true)
                    )
                }
            )
        }

        composable(
            route = Screen.SingleCall.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("isOutgoing") { type = NavType.BoolType; defaultValue = false },
                navArgument("targetName") { type = NavType.StringType; defaultValue = "Caller" },
                navArgument("targetPhone") { type = NavType.StringType; defaultValue = "" },
                navArgument("isVideo") { type = NavType.BoolType; defaultValue = true },
                navArgument("isSoloTest") { type = NavType.BoolType; defaultValue = false }
            )
        ) { backStackEntry ->
            val uid = getActiveUid() ?: return@composable
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val isOutgoing = backStackEntry.arguments?.getBoolean("isOutgoing") ?: false
            val targetName = backStackEntry.arguments?.getString("targetName") ?: "Caller"
            val targetPhone = backStackEntry.arguments?.getString("targetPhone") ?: ""
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: true
            val isSoloTest = backStackEntry.arguments?.getBoolean("isSoloTest") ?: false

            SingleCallScreen(
                callId = callId,
                currentUid = uid,
                isOutgoing = isOutgoing,
                targetName = targetName,
                targetPhone = targetPhone,
                isVideo = isVideo,
                isSoloTest = isSoloTest,
                onCallEnded = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.GroupCall.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("groupId") { type = NavType.StringType; defaultValue = "" },
                navArgument("groupName") { type = NavType.StringType; defaultValue = "Group Call" },
                navArgument("isVideo") { type = NavType.BoolType; defaultValue = true }
            )
        ) { backStackEntry ->
            val uid = getActiveUid() ?: return@composable
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""
            val groupName = backStackEntry.arguments?.getString("groupName") ?: "Group Call"
            val isVideo = backStackEntry.arguments?.getBoolean("isVideo") ?: true

            GroupCallScreen(
                callId = callId,
                groupId = groupId,
                groupName = groupName,
                currentUid = uid,
                isVideo = isVideo,
                onCallEnded = { navController.popBackStack() }
            )
        }

        composable(
            route = Screen.IncomingCall.route,
            arguments = listOf(
                navArgument("callId") { type = NavType.StringType },
                navArgument("callerName") { type = NavType.StringType; defaultValue = "Incoming" },
                navArgument("callerPhone") { type = NavType.StringType; defaultValue = "" },
                navArgument("callType") { type = NavType.StringType; defaultValue = "VIDEO" },
                navArgument("isGroupCall") { type = NavType.BoolType; defaultValue = false },
                navArgument("groupId") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val callId = backStackEntry.arguments?.getString("callId") ?: ""
            val callerName = backStackEntry.arguments?.getString("callerName") ?: "Incoming"
            val callerPhone = backStackEntry.arguments?.getString("callerPhone") ?: ""
            val callType = backStackEntry.arguments?.getString("callType") ?: "VIDEO"
            val isGroupCall = backStackEntry.arguments?.getBoolean("isGroupCall") ?: false
            val groupId = backStackEntry.arguments?.getString("groupId") ?: ""

            IncomingCallScreen(
                callId = callId,
                callerName = callerName,
                callerPhone = callerPhone,
                callType = callType,
                isGroupCall = isGroupCall,
                groupId = groupId,
                onAcceptCall = {
                    if (isGroupCall) {
                        navController.navigate(
                            Screen.GroupCall.createRoute(callId, groupId, callerName, callType == "VIDEO")
                        ) {
                            popUpTo(Screen.IncomingCall.route) { inclusive = true }
                        }
                    } else {
                        navController.navigate(
                            Screen.SingleCall.createRoute(callId, false, callerName, callerPhone, callType == "VIDEO")
                        ) {
                            popUpTo(Screen.IncomingCall.route) { inclusive = true }
                        }
                    }
                },
                onDeclineCall = {
                    navController.popBackStack()
                }
            )
        }
    }
}
