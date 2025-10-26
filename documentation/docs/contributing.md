---
title: 🤝🏼 Contributing
sidebar_position: 10
---

# Contributing

NotallyX is an open-source project, and contributions from the community are welcome and appreciated. This guide explains how you can contribute to the project, whether you're a developer, translator, or user.

## Ways to Contribute

There are many ways to contribute to NotallyX, regardless of your technical skills:

### For Everyone

- **Report bugs**: If you find a bug, report it on [GitHub Issues](https://github.com/PhilKes/NotallyX/issues/new/choose)
- **Suggest features**: Have an idea for a new feature? Share it on [GitHub Issues](https://github.com/PhilKes/NotallyX/issues/new/choose)
- **Improve documentation**: Help improve this documentation by suggesting changes or additions
- **Spread the word**: Share NotallyX with others who might find it useful
- **Translate the app**: Help make NotallyX available in more languages, see `Contributing Translations` below

### For Developers

- **Fix bugs**: Pick an open issue and submit a pull request to fix it
- **Implement features**: Work on new features that have been approved
- **Improve performance**: Help optimize the app's performance
- **Write tests**: Improve test coverage to ensure reliability

## Getting Started with Development

If you want to contribute code to NotallyX, follow these steps:

### Setting Up the Development Environment

1. **Fork the repository**: Go to [NotallyX on GitHub](https://github.com/PhilKes/NotallyX) and click the "Fork" button
2. **Clone your fork**: 
   ```bash
   git clone https://github.com/YOUR_USERNAME/NotallyX.git
   ```
3. **Set up Android Studio**:
   - Download and install [Android Studio](https://developer.android.com/studio)
   - Open the cloned project in Android Studio
   - Let Gradle sync the project dependencies

4. **Configure an emulator or device**:
   - Set up an Android emulator through AVD Manager in Android Studio, or
   - Connect a physical Android device with USB debugging enabled

### Making Changes

1. **Create a new branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
   or
   ```bash
   git checkout -b fix/issue-you-are-fixing
   ```

2. **Make your changes**: Implement your feature or fix the bug

3. **Follow coding standards**:
   - Write code in Kotlin
   - Follow the project's coding style
   - Run `./gradlew ktfmtFormat` before committing to ensure proper formatting

4. **Test your changes**:
   - Run existing tests: `./gradlew test`
   - Test on different Android versions if possible
   - Ensure your changes don't break existing functionality

5. **Commit your changes**:
   ```bash
   git add .
   git commit -m "Brief description of your changes"
   ```

6. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```

7. **Create a pull request**:
   - Go to your fork on GitHub
   - Click "New pull request"
   - Select your branch and submit the pull request
   - Provide a clear description of what your changes do

## Contributing Translations

To contribute translations:

1. Download the current [translations.xlsx](https://github.com/PhilKes/NotallyX/raw/refs/heads/main/app/translations.xlsx) file
2. Open it in Excel/LibreOffice and add missing translations
   - Missing translations are marked in red
   - You can filter by key or any language column values
   - Non-translatable strings are hidden and marked in gray, do not add translations for them
   - For plurals, some languages need/have more quantity strings than others
3. Open an [Update Translations Issue](https://github.com/PhilKes/NotallyX/issues/new?assignees=&labels=translations&projects=&template=translation.md&title=%3CINSERT+LANGUAGE+HERE%3E+translations+update)
4. The project maintainer will create a Pull Request to add your updated translations

## Bug Reports and Feature Requests

### Reporting Bugs

When reporting a bug, please include:

1. Steps to reproduce the issue
2. What you expected to happen
3. What actually happened
4. Your device information (Android version, device model)
5. Screenshots if applicable

Use the bug report template when creating a new issue on GitHub.

### Requesting Features

When suggesting a new feature:

1. Clearly describe the feature and its purpose
2. Explain how it would benefit users
3. Provide examples of how it might work
4. Consider potential implementation challenges

Use the feature request template when creating a new issue on GitHub.

## Code of Conduct

By participating in this project, you agree to maintain a respectful and inclusive environment for everyone. This includes:

- Using welcoming and inclusive language
- Being respectful of differing viewpoints and experiences
- Gracefully accepting constructive criticism
- Focusing on what is best for the community
- Showing empathy towards other community members

## Getting Help

If you need help with contributing:

- Check the [FAQ](faq.md) for common questions
- Ask for help in the GitHub issue you're working on
- Contact the project maintainer through GitHub
