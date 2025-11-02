package com.philkes.notallyx.utils.security

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.IvParameterSpec

private const val ENCRYPTION_KEY_NAME = "notallyx_database_encryption_key"

private const val ANDROID_KEYSTORE = "AndroidKeyStore"

fun encryptDatabase(context: ContextWrapper, dbFile: File, passphrase: ByteArray) {
    if (dbFile.isUnencryptedDatabase) {
        try {
            SQLCipherUtils.encrypt(context, dbFile, passphrase)
            if (dbFile.isUnencryptedDatabase) {
                throw EncryptionException(
                    "Encrypt was executed, but database is still not encrypted"
                )
            }
        } catch (e: Exception) {
            throw EncryptionException("Encryption of ${dbFile.name} failed", e)
        }
    }
}

fun decryptDatabase(context: ContextWrapper, dbFile: File, passphrase: ByteArray) {
    if (dbFile.isEncryptedDatabase) {
        try {
            SQLCipherUtils.decrypt(context, dbFile, passphrase)
            if (SQLCipherUtils.getDatabaseState(dbFile) == SQLCipherUtils.State.ENCRYPTED) {
                throw DecryptionException(
                    "Decrypt was executed, but database is still not decrypted"
                )
            }
        } catch (e: Exception) {
            throw DecryptionException("Decryption of ${dbFile.name} failed", e)
        }
    }
}

val File.isEncryptedDatabase: Boolean
    get() = SQLCipherUtils.getDatabaseState(this) == SQLCipherUtils.State.ENCRYPTED

val File.isUnencryptedDatabase: Boolean
    get() = SQLCipherUtils.getDatabaseState(this) == SQLCipherUtils.State.UNENCRYPTED

fun decryptDatabase(
    context: Context,
    passphrase: ByteArray,
    databaseFile: File,
    decryptedFile: File,
) {
    val state = SQLCipherUtils.getDatabaseState(databaseFile)
    if (state == SQLCipherUtils.State.ENCRYPTED) {
        SQLCipherUtils.decrypt(context, databaseFile, decryptedFile, passphrase)
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
fun getCipher(): Cipher {
    return Cipher.getInstance(
        KeyProperties.KEY_ALGORITHM_AES +
            "/" +
            KeyProperties.BLOCK_MODE_CBC +
            "/" +
            KeyProperties.ENCRYPTION_PADDING_PKCS7
    )
}
