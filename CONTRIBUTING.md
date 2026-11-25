# Contributing to SerieusTracker

Thank you for wanting to help! This is a small, privacy-focused project, and contributions of all sizes are welcome: bug reports, translations, documentation and code.

## Contents

- [Principles](#principles)
- [What is welcome, and what is not](#what-is-welcome-and-what-is-not)
- [Reporting bugs and suggesting features](#reporting-bugs-and-suggesting-features)
- [Setting up](#setting-up)
- [Making a change](#making-a-change)
- [Code style](#code-style)
- [Translations](#translations)
- [Changing the export/import format](#changing-the-exportimport-format)
- [Pull request checklist](#pull-request-checklist)
- [Content policy](#content-policy)
- [Licensing of contributions](#licensing-of-contributions)

---

## Principles

Changes are judged against a few simple principles. If you are unsure whether an idea fits, open an issue first and ask.

1. **Local first.** The user's list lives on the device. There is no SerieusTracker server and no account.
2. **No tracking.** No analytics, advertising, crash-reporting services or other third-party trackers.
3. **Simple and readable.** The export file stays small and human-readable, and the code stays easy to follow.
4. **Additive and safe.** Operations that import or merge data never delete or unmark what the user already has.
5. **Open.** Dependencies must be open source, so that the app can be built without proprietary components.

## What is welcome, and what is not

**Welcome**

- Bug fixes, with a clear description of the problem.
- Translations (see [Translations](#translations)).
- Improvements to documentation, wording and accessibility.
- Tests, especially for the pure logic such as `ShowListFile`.
- New features that fit the principles above. Please open an issue first for anything larger than a small change.

**Will not be accepted**

- Anything that links to, embeds, streams or helps to find **pirated or otherwise unlicensed content**, and anything intended to bypass a publisher's or service's access controls. See the [content policy](#content-policy).
- Analytics, ads, tracking or telemetry of any kind.
- Sending the user's list or watch history to any server that is not chosen and operated by the user.
- New data sources or network services without a prior discussion in an issue.
- Proprietary or closed-source dependencies.
- Large reformatting or refactoring that is mixed with functional changes.

## Reporting bugs and suggesting features

Use the issue forms: **Issues → New issue**. A good report says what you did, what you expected and what happened, and includes the app version and Android version.

**Never post your Plex token** or other credentials in an issue, a screenshot or a log. For a security problem, do not open a public issue; follow [SECURITY.md](SECURITY.md).

## Setting up

1. Fork the repository and clone your fork.
2. Open the project in a recent Android Studio and let Gradle sync.
3. Run the `app` configuration on a device or emulator.

No API keys are needed. To try the Plex features you need a Plex account, and for the watch history import a Plex Media Server that the device can reach.

See the [README](README.md) for an overview and the project structure.

## Making a change

1. Create a branch from `main` with a short, descriptive name, such as `fix-season-menu` or `add-german-translation`.
2. Keep the change focused. One pull request should do one thing.
3. Test it on a device or emulator, in both light and dark theme if you changed the UI.
4. Commit with a clear message in English, in the imperative mood: `Fix crash when a show has no episodes`.
5. Open a pull request against `main`. Describe what changed and why, and link the issue it closes (`Closes #123`).

Expect a review with some comments. Please keep discussions friendly and respectful.

## Code style

- Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html). Android Studio's default formatter is fine.
- Prefer small, stateless composables. State lives in `AppViewModel`, and the network is reached only through `ShowRepository`.
- Add a short KDoc comment to anything whose purpose is not obvious from its name. Explain *why*, not *what*.
- Do not add a dependency for something small that the platform or the standard library already offers. The app deliberately uses `org.json` instead of a JSON library.
- **No hard-coded text in the UI.** All user-visible text goes in string resources. In ViewModel code, use `UiText`.
- Handle network failures. A failed request must never crash the app or lose data.

## Translations

The app has English (default) and Swedish. To add a language:

1. Copy `app/src/main/res/values/strings.xml` to `app/src/main/res/values-xx/strings.xml`, where `xx` is the language code (`values-de`, `values-fr`, and so on).
2. Translate every string. Keep placeholders such as `%1$d` and `%2$s` and their numbering, and write apostrophes as `\'`.
3. Do not translate the field names of the export file (`source`, `id`, `seen`, ...) or the example in the settings screen. They are part of a file format, not of the interface.
4. Build and select the language in Settings. It shows up in the drop-down by itself.

Improvements to an existing translation are just as welcome. If you add or change an English string, update the other languages too, or say in the pull request which ones still need work.

## Changing the export/import format

The export file is documented in [docs/FILE_FORMAT.md](docs/FILE_FORMAT.md) and implemented in `ShowListFile.kt`. People write these files by hand and with scripts, so changes need extra care:

- Keep the format backward compatible whenever possible. Unknown fields are ignored on import so that fields can be added.
- Update `docs/FILE_FORMAT.md` and the help text in the app in the same pull request.
- Test a round trip: export, import into an empty list, export again, and compare.
- Remember that import must stay **additive**.

## Pull request checklist

Before you open a pull request, please check that:

- [ ] The project builds and the changed feature works on a device or emulator.
- [ ] The change is focused and described clearly.
- [ ] No hard-coded user-visible text, and all languages are updated or noted.
- [ ] Documentation is updated (`README.md`, `docs/FILE_FORMAT.md`) if behaviour changed.
- [ ] No secrets, tokens, keystores or personal data are included.
- [ ] No new dependency, tracker or network service was added without discussion.
- [ ] The change follows the [content policy](#content-policy).

## Content policy

SerieusTracker is a tool for **keeping track of what you watch**. It does not provide, link to or help to find content, and it must not be turned into something that does.

- The only metadata sources are AniList and Plex (including the user's own Plex Media Server).
- Contributions that add links or integrations to pirated, scraped or unlicensed content, streaming-site scrapers, or ways around access controls will be closed without merging.
- Posters and other artwork come from the data sources at run time. Do not add copyrighted artwork, logos or screenshots of third-party content to the repository.

If you fork the project, you are responsible for your fork. Forks must not use the SerieusTracker name or icon in a way that suggests they are the original project.

## Licensing of contributions

By submitting a contribution you confirm that you wrote it or have the right to submit it, and you agree that it is licensed under the same license as the project (see the `LICENSE` file).
