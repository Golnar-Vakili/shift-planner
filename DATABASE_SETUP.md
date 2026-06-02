# How to set up the database (MySQL)

You only need to do this **once**. After that, the app will save users and help requests in MySQL.

---

## Step 1: Open MySQL Workbench

- Start **MySQL Workbench**.
- Double-click your connection **"hsnr"** (root @ 127.0.0.1:3306).
- Enter your MySQL password if it asks. You should see a big SQL editor window.

---

## Step 2: Create the database

In the SQL editor (the white area where you can type):

1. Copy and paste this **exactly**:

```sql
CREATE DATABASE IF NOT EXISTS shiftplanner
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
```

2. Click the **lightning bolt** icon (Execute) or press **Ctrl+Shift+Enter**.
3. You should see a green checkmark or "Success". That’s it — the database `shiftplanner` is created.

---

## Step 3: Tell the app your MySQL password

1. In your project, open this file:  
   **employee-login** → **src** → **main** → **resources** → **application-mysql.properties**

2. Find this line:
   ```properties
   spring.datasource.password=
   ```

3. Put your MySQL password **between the equals sign and the end of the line**.
   - If your password is `mypass123`, the line should be:
     ```properties
     spring.datasource.password=mypass123
     ```
   - If your MySQL user has **no password**, leave it as:
     ```properties
     spring.datasource.password=
     ```

4. Save the file (Ctrl+S).

---

## Step 4: Run the app

1. In IntelliJ, stop the app if it’s already running (red square).
2. Run **EmployeeLoginApplication** again (green play).
3. When it starts, open **http://localhost:8080** in your browser.

---

## What happens now?

- When someone **registers**, the user is saved in the **shiftplanner** database.
- When someone submits a **help request**, it is saved there too.
- If you **restart the app** or **refresh the page**, the data is still there because it’s in MySQL, not in memory.

---

## If something goes wrong

**“Unknown database 'shiftplanner'”**  
→ Step 2 didn’t run correctly. Open Workbench, run the `CREATE DATABASE...` again, and check for errors.

**“Access denied for user 'root'”**  
→ The password in **application-mysql.properties** doesn’t match your MySQL password. Fix the password in that file and run the app again.

**“Port 8080 is already in use”**  
→ Stop the previous run of the app in IntelliJ (red square), then run it again.
