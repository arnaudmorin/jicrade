package fr.amorin.jicrade.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import fr.amorin.jicrade.BuildConfig
import fr.amorin.jicrade.data.local.AlertDao
import fr.amorin.jicrade.data.local.JicradeDb
import fr.amorin.jicrade.data.local.ScheduleDao
import fr.amorin.jicrade.data.local.UserDao
import fr.amorin.jicrade.data.remote.AuthInterceptor
import fr.amorin.jicrade.data.remote.JiraApi
import fr.amorin.jicrade.data.remote.OpsApi
import fr.amorin.jicrade.data.remote.RetryInterceptor
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun json(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    @Provides
    @Singleton
    fun okHttp(auth: AuthInterceptor): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(auth)
        .addInterceptor(RetryInterceptor())
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
            }
        }
        .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.atlassian.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun opsApi(retrofit: Retrofit): OpsApi = retrofit.create(OpsApi::class.java)

    @Provides
    @Singleton
    fun jiraApi(retrofit: Retrofit): JiraApi = retrofit.create(JiraApi::class.java)

    @Provides
    @Singleton
    fun db(@ApplicationContext context: Context): JicradeDb =
        Room.databaseBuilder(context, JicradeDb::class.java, "jicrade.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun alertDao(db: JicradeDb): AlertDao = db.alertDao()

    @Provides
    fun userDao(db: JicradeDb): UserDao = db.userDao()

    @Provides
    fun scheduleDao(db: JicradeDb): ScheduleDao = db.scheduleDao()
}
