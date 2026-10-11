# JCKC Accounting and Bookkeeping Services App

A Windows desktop application that keeps a bookkeeping office's client records, SLSP reports and BIR confirmation emails in one place. It cuts down repeated typing, manual copying of templates, and switching between separate programs.

**Release:** 1.0.0 &nbsp;|&nbsp; **Developed by:** Wakai One

## Features

| Feature | What it does |
|---|---|
| **Taxpayer Information Management** | Keep an organized list of taxpayer clients. Search by TIN, name or trade name, then view, add, edit or delete a record, including personal information and email accounts. |
| **SLSP Generation** | Create a Summary List of Sales and Purchases per month, with inline editing, automatic VAT computations, print preview, PDF printing and Excel export. |
| **BIR Confirmation Email Integration** | Read BIR confirmation emails from the taxpayer's Gmail or Yahoo account inside the app, then review and print them. |
| **Built-in Sum Calculator** | Quick calculations inside the SLSP Manager, with no separate calculator needed. |
| **Supplier Management** | Save suppliers (name, TIN, address) once and reuse them when entering purchases. |

### SLSP Manager

- Separate **Sale** and **Purchase** tables, edited directly in the grid. Tab, Enter and the arrow keys move between cells, and new rows can be added from the keyboard.
- Calculated columns (VAT Receipts / Purchases at 1.12, Output / Input Tax at 12%, totals) are filled in automatically and totalled at the bottom of each table.
- Dates are limited to the days of the selected month and year.
- Purchases use a supplier dropdown that shows `Trade Name: TIN`.
- Undo and redo, an unsaved-changes marker (`*`) and a confirmation prompt before leaving the page.
- Hide or show the VAT Exempt, Zero-rated and Total Amount columns without losing their values.

### Printing and Excel export

From the **Summary List of Sales and Purchases**, click **Print** to open the print screen:

- Choose **Whole year** or **By quarter**, and include **Sales**, **Purchases** or both.
- A live preview shows what will be printed. Months that have no SLSP are listed in red.
- **Print** sends the report to a printer or to **Save as PDF**.
- **Export to Excel** produces a spreadsheet for manual editing.

### BIR Confirmation emails

- The taxpayer needs a Gmail or Yahoo account saved in their record, plus an **app password**.
- If both accounts are saved, Gmail is checked first.
- Messages from `ebirforms-noreply@bir.gov.ph` and `esubmission@bir.gov.ph` appear in a list that can be previewed and printed.
- Fetched emails are saved on the computer, so older emails open quickly and only new ones are downloaded on the next refresh. **Clear Cache** removes the saved copies and downloads them again.
- Downloads keep running in the background if you leave the page.

## System requirements

- Windows 10 or Windows 11 (64-bit)
- No separate Java installation is needed. The installer includes everything the app needs.
- An internet connection for the BIR Confirmation Email feature

## Installation

1. Download the installer, `JCKC_App-1.0.0.msi`.
2. Double-click it to start the installation.
3. If Windows shows **"Windows protected your PC"**, click **More info**, then **Run anyway**. This appears because the installer is not digitally signed.
4. Wait for the installation to finish, then open the app from the Desktop shortcut or the Start Menu.

To uninstall, open **Settings → Apps → Installed apps**, find **JCKC_App** and choose **Uninstall**. Your data file is not deleted.

## Getting started

1. Open the app and enter your password on the login screen, then click **Submit**.
2. Click **New** to add a taxpayer, or search for an existing one.
3. Select a taxpayer, then use **SLSP** to manage their reports or **Check BIR Confirmation** to read their confirmation emails.

To use the BIR Confirmation feature, open **Check BIR Confirmation**. If no app password is saved yet, the **Manage App Password** page opens, where you can go straight to the Gmail or Yahoo page that creates one.

## Keyboard shortcuts

Shortcuts are shown in the footer of each screen. The main ones:

| Screen | Shortcuts |
|---|---|
| **Taxpayer list (Home)** | `Esc` Logout · `Ctrl+N` New · `Ctrl+E` Edit · `Ctrl+W` Delete · `Ctrl+F` Find · `Up/Down` Navigate · `Ctrl+P` Print · `F1` Check BIR Confirmation · `F2` SLSP |
| **SLSP list** | `Esc` Back · `Ctrl+N` New · `Ctrl+F` Find · `Up/Down` Navigate · `Enter` Open SLSP · `Ctrl+P` Print |
| **SLSP Manager** | `Esc` Back · `Ctrl+S` Save · `Ctrl+D` Delete SLSP · `Ctrl+N` New row · `Ctrl+W` Delete row · `Ctrl+B` Suppliers · `Ctrl+Z` Undo · `Ctrl+Y` Redo · `Ctrl+E` Go to the Sum Calculator · `Ctrl+Delete` Clear the calculator |
| **Print SLSP** | `Esc` Back · `Ctrl+P` Print · `Ctrl+E` Export to Excel · `Ctrl +` / `Ctrl -` / scroll to zoom · `Ctrl+Num0` Reset zoom |
| **Suppliers** | `Esc` Back · `Ctrl+Enter` Save · `Up/Down` Navigate · `Ctrl+W` Delete |
| **BIR Confirmation** | `Esc` Back · `Ctrl+R` Refresh · `Ctrl+E` Edit app password · `Ctrl+F` Find · `Ctrl+P` Print · `Ctrl+W` Clear cache |
| **Manage App Password** | `Esc` Back · `Enter` Save / Edit · `Ctrl+E` Configure · `Ctrl+B` Open the provider's app-password page |
| **Add / Edit Taxpayer** | `Esc` Back · `Ctrl+Enter` Done |

## Your data

All data is stored in a single file on your computer:

```
C:\Users\[YourName]\.jckc\jckc-app.db
```

**Back up regularly, and before every update:**

1. Close the app.
2. Open `C:\Users\[YourName]\.jckc` in File Explorer (type the path into the address bar if the folder is hidden).
3. Copy `jckc-app.db` to a flash drive or cloud folder, adding the date to the name, for example `jckc-app-2026-10-12.db`.

**Restore:** close the app, copy your backup into the `.jckc` folder, rename it to `jckc-app.db` (replacing the existing file), then open the app.

> **Warning:** deleting a taxpayer also removes their personal information, accounts, suppliers, SLSP records and saved emails. This cannot be undone.

## Security notes

- The data file contains client information and the saved email app passwords. Keep it, and any backups of it, in a protected location.
- Use an **app password** for Gmail or Yahoo, never the account's main password.
- The installer is not digitally signed, which is why Windows shows its warning.

## Troubleshooting

| Problem | What to try |
|---|---|
| The installer shows "Windows protected your PC" | Click **More info**, then **Run anyway**. |
| The app does not open | Restart the computer and try again. If it still fails, contact support. |
| Data looks empty after install | Check that `jckc-app.db` exists in `C:\Users\[YourName]\.jckc`. Restore from a backup if needed. |
| BIR emails do not load | Check the internet connection, and the email account and app password saved for the taxpayer. |
| Excel or PDF export fails | Make sure the file is not already open in another program, then try again. |

## Built with

- **Java** and **JavaFX** (desktop interface, FXML layouts)
- **SQLite** through the `sqlite-jdbc` driver (local database)
- **Jakarta Mail** (reading Gmail and Yahoo over IMAP)
- **Apache POI** (Excel export)
- **Ikonli** with Font Awesome 5 (icons)
- **jBCrypt** (password hashing for the login)

## Project structure

The project is a Java module (`JCKC_App`) with a view / view-model / repository layout:

```
app/
├── Main.java          Application entry point
├── models/            Data classes (Taxpayer, SLSP, Sale, Purchase, Supplier, Account, ...)
├── database/          SQLite repositories and the database connection
├── viewmodels/        Screen logic (taxpayers, SLSP manager, suppliers, BIR mail, app password)
├── views/             JavaFX controllers and reusable cards
│   └── fxml/          FXML layouts, application.css and images
└── export/            Excel export (if present in your copy)
```

### Building from source

1. Install a JDK that supports the language level used by the project (the code uses unnamed variables, so JDK 22 or newer) and the JavaFX SDK.
2. Put the dependencies on the **module path**: JavaFX, `sqlite-jdbc`, Ikonli (core, javafx, fontawesome5), `jbcrypt`, Jakarta Mail, and Apache POI together with the jars it depends on (commons-io, commons-codec, commons-collections4, commons-math3, commons-lang3, commons-compress, log4j-api, SparseBitSet, xmlbeans, curvesapi).
3. Make sure `module-info.java` requires the modules you use, including `java.desktop` (used to open the browser from the Manage App Password page).
4. Run `app.Main`.

POI and the other jars without a `module-info` become *automatic modules*. If a jar's derived name differs from the name another jar asks for, use a newer version of that jar that declares its module name.

## Support

**Joven K Claudio** — jovenkclaudio24@gmail.com

More improvements and bug fixes will come in future releases.
