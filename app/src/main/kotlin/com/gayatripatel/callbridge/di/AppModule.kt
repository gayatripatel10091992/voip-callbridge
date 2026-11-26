package com.gayatripatel.callbridge.di

import com.gayatripatel.callbridge.billing.PlayBillingGateway
import com.gayatripatel.callbridge.billing.SubscriptionGateway
import com.gayatripatel.callbridge.data.Clock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    @Singleton
    abstract fun subscriptionGateway(impl: PlayBillingGateway): SubscriptionGateway

    companion object {
        @Provides
        fun clock(): Clock = Clock { System.currentTimeMillis() }

        @Provides
        fun zone(): ZoneId = ZoneId.systemDefault()
    }
}
