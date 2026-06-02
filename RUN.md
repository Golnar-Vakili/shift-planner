# How to run employee-login

## 1. Use JDK 21 (important)

The project is built for **Java 21**. Running with JDK 24 can cause startup failures.

- **File → Project Structure → Project** → set **Project SDK** to **21** (e.g. openjdk-21).
- If you don’t have JDK 21: **File → Project Structure → SDKs** → **+** → Download JDK → choose **Version 21**.

## 2. Use the “local” profile (no MySQL needed)

- Either use the run configuration **“EmployeeLoginApplication”** from the run dropdown (it has `ACTIVE_PROFILES=local`).
- Or in your current run configuration: **Run → Edit Configurations** → select the app → **Active profiles:** set to **local**.

With `local`, the app uses an in-memory H2 database and should start without MySQL.

## 3. If it still exits with code 1

- In **Run → Edit Configurations**, add **Program arguments** (not VM options): `--debug`
- Run again and **scroll up** in the Run console to see the first **“Caused by:”** line; that is the real error.
- Or open **employee-login.log** in the project folder after a failed run and check the end of the file for the stack trace.

## 4. When you want to use MySQL

- Create the database in MySQL Workbench:  
  `CREATE DATABASE IF NOT EXISTS shiftplanner CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;`
- Set the password in **application-mysql.properties** (`spring.datasource.password=...`).
- Set **Active profiles** to **mysql** (or in **application.properties** set `spring.profiles.active=mysql`).
