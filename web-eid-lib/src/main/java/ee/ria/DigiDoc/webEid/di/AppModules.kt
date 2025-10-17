@file:Suppress("PackageName")

package ee.ria.DigiDoc.webEid.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ee.ria.DigiDoc.webEid.WebEidAuthService
import ee.ria.DigiDoc.webEid.WebEidAuthServiceImpl
import ee.ria.DigiDoc.webEid.WebEidSignService
import ee.ria.DigiDoc.webEid.WebEidSignServiceImpl

@Module
@InstallIn(SingletonComponent::class)
class AppModules {
    @Provides
    fun provideWebEidAuthService(): WebEidAuthService = WebEidAuthServiceImpl()

    @Provides
    fun provideWebEidSignService(): WebEidSignService = WebEidSignServiceImpl()
}
