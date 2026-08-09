package dev.bartuzen.qbitcontroller.ui.settings.prowlarr

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bartuzen.qbitcontroller.model.ProwlarrConfig
import dev.bartuzen.qbitcontroller.network.supportsSelfSignedCertificates
import dev.bartuzen.qbitcontroller.preferences.SwitchPreference
import dev.bartuzen.qbitcontroller.ui.components.ActionMenuItem
import dev.bartuzen.qbitcontroller.ui.components.AppBarActions
import dev.bartuzen.qbitcontroller.ui.components.SwipeableSnackbarHost
import dev.bartuzen.qbitcontroller.ui.settings.addeditserver.isPlatformUrlValid
import dev.bartuzen.qbitcontroller.utils.EventEffect
import dev.bartuzen.qbitcontroller.utils.getErrorMessage
import dev.bartuzen.qbitcontroller.utils.getString
import dev.bartuzen.qbitcontroller.utils.stringResource
import dev.bartuzen.qbitcontroller.utils.stringResourceSaver
import dev.bartuzen.qbitcontroller.utils.topAppBarColors
import io.ktor.http.parseUrl
import kotlinx.coroutines.launch
import org.koin.compose.viewmodel.koinViewModel
import qbitcontroller.composeapp.generated.resources.Res
import qbitcontroller.composeapp.generated.resources.error_required_field
import qbitcontroller.composeapp.generated.resources.settings_category_prowlarr
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_action_save
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_api_key
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_connection_success
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_enable
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_save_success
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_show_bottom_nav
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_test_connection
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_url
import qbitcontroller.composeapp.generated.resources.settings_prowlarr_url_hint
import qbitcontroller.composeapp.generated.resources.settings_server_invalid_url
import qbitcontroller.composeapp.generated.resources.settings_server_trust_self_signed_certificates

@Composable
fun ProwlarrSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProwlarrSettingsViewModel = koinViewModel(),
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val isTesting by viewModel.isTesting.collectAsStateWithLifecycle()

    val config = viewModel.config
    var url by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(config.url))
    }
    var apiKey by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue(config.apiKey))
    }
    var isEnabled by rememberSaveable { mutableStateOf(config.isEnabled) }
    var trustSelfSignedCertificates by rememberSaveable { mutableStateOf(config.trustSelfSignedCertificates) }

    var urlError by rememberSaveable(
        stateSaver = stringResourceSaver(Res.string.error_required_field, Res.string.settings_server_invalid_url),
    ) { mutableStateOf(null) }

    fun validateAndGetConfig(): ProwlarrConfig? {
        val urlWithProtocol = if (!url.text.contains("://")) "http://${url.text}" else url.text
        urlError = if (url.text.isBlank()) {
            Res.string.error_required_field
        } else if (parseUrl(urlWithProtocol) == null || !isPlatformUrlValid(urlWithProtocol)) {
            Res.string.settings_server_invalid_url
        } else {
            null
        }

        if (urlError != null) {
            return null
        }

        return ProwlarrConfig(
            url = url.text,
            apiKey = apiKey.text,
            isEnabled = isEnabled,
            trustSelfSignedCertificates = trustSelfSignedCertificates,
        )
    }

    EventEffect(viewModel.eventFlow) { event ->
        when (event) {
            is ProwlarrSettingsViewModel.Event.TestFailure -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                scope.launch {
                    snackbarHostState.showSnackbar(getErrorMessage(event.error))
                }
            }
            ProwlarrSettingsViewModel.Event.TestSuccess -> {
                snackbarHostState.currentSnackbarData?.dismiss()
                scope.launch {
                    snackbarHostState.showSnackbar(getString(Res.string.settings_prowlarr_connection_success))
                }
            }
        }
    }

    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    val scrollState = rememberScrollState()
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(Res.string.settings_category_prowlarr),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            softwareKeyboardController?.hide()
                            onNavigateBack()
                        },
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                        )
                    }
                },
                actions = {
                    AppBarActions(
                        items = listOf(
                            ActionMenuItem(
                                title = stringResource(Res.string.settings_prowlarr_action_save),
                                icon = Icons.Filled.Save,
                                onClick = {
                                    val newConfig = validateAndGetConfig() ?: return@ActionMenuItem
                                    softwareKeyboardController?.hide()
                                    viewModel.saveConfig(newConfig)
                                    scope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        snackbarHostState.showSnackbar(
                                            getString(Res.string.settings_prowlarr_save_success),
                                        )
                                    }
                                },
                                showAsAction = true,
                            ),
                        ),
                    )
                },
                colors = scrollState.topAppBarColors(),
            )
        },
        snackbarHost = {
            SwipeableSnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom)),
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        if (it.text != url.text) {
                            urlError = null
                        }
                        url = it
                    },
                    label = {
                        Text(
                            text = stringResource(Res.string.settings_prowlarr_url),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Link,
                            contentDescription = null,
                        )
                    },
                    supportingText = {
                        Text(
                            text = urlError?.let { stringResource(it) }
                                ?: stringResource(Res.string.settings_prowlarr_url_hint),
                        )
                    },
                    isError = urlError != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(),
                )

                var showApiKey by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            text = stringResource(Res.string.settings_prowlarr_api_key),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Filled.Key,
                            contentDescription = null,
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                    ),
                    visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showApiKey = !showApiKey }) {
                            Icon(
                                imageVector = if (showApiKey) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                                contentDescription = null,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(4.dp))

                SwitchPreference(
                    value = isEnabled,
                    onValueChange = { isEnabled = it },
                    title = { Text(text = stringResource(Res.string.settings_prowlarr_enable)) },
                )

                if (supportsSelfSignedCertificates()) {
                    SwitchPreference(
                        value = trustSelfSignedCertificates,
                        onValueChange = { trustSelfSignedCertificates = it },
                        title = { Text(text = stringResource(Res.string.settings_server_trust_self_signed_certificates)) },
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                OutlinedButton(
                    onClick = {
                        val newConfig = validateAndGetConfig() ?: return@OutlinedButton
                        viewModel.testConnection(newConfig)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Filled.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(text = stringResource(Res.string.settings_prowlarr_test_connection))
                }

                Spacer(modifier = Modifier.height(4.dp))

                val showProwlarrTab by viewModel.showProwlarrTab.flow.collectAsStateWithLifecycle()
                SwitchPreference(
                    value = showProwlarrTab,
                    onValueChange = { viewModel.showProwlarrTab.value = it },
                    title = { Text(text = stringResource(Res.string.settings_prowlarr_show_bottom_nav)) },
                )

                Spacer(
                    modifier = Modifier.windowInsetsBottomHeight(WindowInsets.safeDrawing),
                )
            }

            AnimatedVisibility(
                visible = isTesting,
                enter = expandVertically(tween(durationMillis = 500)),
                exit = shrinkVertically(tween(durationMillis = 500)),
            ) {
                LinearProgressIndicator(
                    strokeCap = StrokeCap.Butt,
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter),
                )
            }
        }
    }
}
