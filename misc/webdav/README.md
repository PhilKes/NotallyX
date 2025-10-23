# WebDAV Local Test

1. Run `docker compose up`
2. Start Android Emulator
3. Install [DAVx⁵](https://f-droid.org/packages/at.bitfire.davdroid/)
4. Open DAVx⁵ -> Menu -> `WebDAV mounts` -> `+`
5. Enter:
 - URL: `http://10.0.2.2:8080` (`10.0.2.2` = `localhost` in Android Emulator)
 - User: webdavuser
 - Password: webdavpass