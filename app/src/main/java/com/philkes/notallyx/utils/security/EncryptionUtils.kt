package com.philkes.notallyx.utils.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import com.philkes.notallyx.data.NotallyDatabase.Companion.DatabaseName
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

private const val ENCRYPTION_KEY_NAME = "notallyx_database_encryption_key"

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

fun encryptDatabase(context: Context, passphrase: ByteArray) {
    val state = SQLCipherUtils.getDatabaseState(context, DatabaseName)
    if (state == SQLCipherUtils.State.UNENCRYPTED) {
        SQLCipherUtils.encrypt(context, DatabaseName, passphrase)
    }
}

fun decryptDatabase(context: Context, passphrase: ByteArray) {
    val state = SQLCipherUtils.getDatabaseState(context, DatabaseName)
    if (state == SQLCipherUtils.State.ENCRYPTED) {
        SQLCipherUtils.decrypt(context, DatabaseName, passphrase)
    }
}

fun decryptDatabase(
    context: Context,
    passphrase: ByteArray,
    decryptedFile: File,
    databaseName: String,
) {
    val state = SQLCipherUtils.getDatabaseState(context, databaseName)
    if (state == SQLCipherUtils.State.ENCRYPTED) {
        SQLCipherUtils.decrypt(context, databaseName, decryptedFile, passphrase)
    }
}

@RequiresApi(Build.VERSION_CODES.M)
private fun getOrCreateSecretKey(keyName: String = ENCRYPTION_KEY_NAME): SecretKey {
    // If Secretkey was previously created for that keyName, then grab and return it.
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
    keyStore.load(null) // Keystore must be loaded before it can be accessed
    keyStore.getKey(keyName, null)?.let {
        return it as SecretKey
    }

    // if you reach here, then a new SecretKey must be generated for that keyName
    val keyGenParams =
        KeyGenParameterSpec.Builder(
                keyName,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
            .setBlockModes(KeyProperties.BLOCK_MODE_CBC)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_PKCS7)
            //        .setUserAuthenticationRequired(true)
            // Invalidate the keys if the user has registered a new biometric
            // credential, such as a new fingerprint. Can call this method only
            // on Android 7.0 (API level 24) or higher. The variable
            // "invalidatedByBiometricEnrollment" is true by default.
            //            .setInvalidatedByBiometricEnrollment(true) // TODO:
            // The other important property is setUserAuthenticationValidityDurationSeconds().
            // If it is set to -1 then the key can only be unlocked using Fingerprint or Biometrics.
            // If it is set to any other value, the key can be unlocked using a device screenlock
            // too.
            .setUserAuthenticationValidityDurationSeconds(-1)
            .build()

    val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    keyGenerator.init(keyGenParams)
    return keyGenerator.generateKey()
}

@RequiresApi(Build.VERSION_CODES.M)
fun getInitializedCipherForEncryption(keyName: String = ENCRYPTION_KEY_NAME): Cipher {
    val cipher = getCipher()
    val secretKey = getOrCreateSecretKey(keyName)
    cipher.init(Cipher.ENCRYPT_MODE, secretKey)
    return cipher
}

@RequiresApi(Build.VERSION_CODES.M)
fun getInitializedCipherForDecryption(
    keyName: String = ENCRYPTION_KEY_NAME,
    iv: ByteArray,
): Cipher {
    val cipher = getCipher()
    val secretKey = getOrCreateSecretKey(keyName)
    cipher.init(Cipher.DECRYPT_MODE, secretKey, IvParameterSpec(iv))
    return cipher
}

@RequiresApi(Build.VERSION_CODES.M)
private fun getCipher(): Cipher {
    return Cipher.getInstance(
        KeyProperties.KEY_ALGORITHM_AES +
            "/" +
            KeyProperties.BLOCK_MODE_CBC +
            "/" +
            KeyProperties.ENCRYPTION_PADDING_PKCS7
    )
}
