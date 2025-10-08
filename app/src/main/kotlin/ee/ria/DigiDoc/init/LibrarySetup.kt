@file:Suppress("PackageName")

package ee.ria.DigiDoc.init

import android.content.Context
import android.widget.Toast
import ee.ria.DigiDoc.R
import ee.ria.DigiDoc.configuration.loader.ConfigurationLoader
import ee.ria.DigiDoc.configuration.utils.TSLUtil
import ee.ria.DigiDoc.cryptolib.init.CryptoInitialization
import ee.ria.DigiDoc.domain.preferences.DataStore
import ee.ria.DigiDoc.libdigidoclib.exceptions.AlreadyInitializedException
import ee.ria.DigiDoc.libdigidoclib.init.Initialization
import ee.ria.DigiDoc.libdigidoclib.init.LibdigidocLibraryLoader
import ee.ria.DigiDoc.utils.snackbar.SnackBarManager
import ee.ria.DigiDoc.utilsLib.logging.LoggingUtil.Companion.errorLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibrarySetup
    @Inject
    constructor(
        private val initialization: Initialization,
        private val cryptoInitialization: CryptoInitialization,
        private val configurationLoader: ConfigurationLoader,
        private val dataStore: DataStore,
        private val libdigidocLibraryLoader: LibdigidocLibraryLoader,
    ) {
        private val logTag = "LibrarySetup"

        suspend fun setupLibraries(
            context: Context,
            isLoggingEnabled: Boolean,
        ) {
            libdigidocLibraryLoader.init(context)

            cryptoInitialization.init(isLoggingEnabled)
            try {
                TSLUtil.setupTSLFiles(context)
                configurationLoader.initConfiguration(
                    context,
                    dataStore.getProxySetting(),
                    dataStore.getManualProxySettings(),
                )
            } catch (ex: Exception) {
                if (ex !is UnknownHostException &&
                    ex !is SocketTimeoutException &&
                    ex !is InterruptedIOException
                ) {
                    errorLog(
                        logTag,
                        "Unable to initialize configuration: ",
                        ex,
                    )
                    withContext(Dispatchers.Main) {
                        SnackBarManager.showMessage(
                            context,
                            R.string.configuration_initialization_failed,
                        )
                    }
                }
            }

            try {
                initialization.init(context, isLoggingEnabled)
            } catch (e: Exception) {
                if (e !is AlreadyInitializedException) {
                    errorLog(logTag, "Unable to initialize libdigidocpp", e)
                    withContext(Dispatchers.Main) {
                        Toast
                            .makeText(
                                context,
                                R.string.libdigidocpp_initialization_failed,
                                Toast.LENGTH_LONG,
                            ).show()
                    }
                }
            }
        }
    }
