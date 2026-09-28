package com.smartlibrary.controller;

import com.smartlibrary.concurrency.AppExecutors;
import com.smartlibrary.concurrency.RefreshQueue;
import com.smartlibrary.data.LibraryRepository;
import com.smartlibrary.data.MemberRepository;
import com.smartlibrary.database.SQLiteConnection;
import com.smartlibrary.json.JsonAuthService;
import com.smartlibrary.json.JsonBinException;
import com.smartlibrary.model.Gender;
import com.smartlibrary.model.Member;
import com.smartlibrary.ui.FormMessage;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 10 - Admin: Manage Members / Registration.
 * Lists existing members, and lets the admin edit or delete them, or
 * register a brand-new student: their profile goes into the SQLite
 * "members" table, and their login credentials (email/password) go into
 * the online JSON on JSONBin.io via JsonAuthService (HTTP GET -> append ->
 * HTTP PUT, parsed/serialized by Jackson) - exactly the split described
 * in the project plan's Registration flow. The studentId written to both
 * sides is the same string, so the online login finds the SQLite profile.
 */
public class MemberController {

    @FXML private TableView<Member> memberTable;
    @FXML private TableColumn<Member, String> studentIdColumn;
    @FXML private TableColumn<Member, String> nameColumn;
    @FXML private TableColumn<Member, String> emailColumn;
    @FXML private TableColumn<Member, String> departmentColumn;
    @FXML private TableColumn<Member, String> phoneColumn;
    @FXML private TableColumn<Member, String> genderColumn;

    @FXML private TextField searchField;

    @FXML private TextField studentIdField;
    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private ComboBox<String> departmentChoiceBox;
    @FXML private TextField phoneField;
    @FXML private ToggleGroup genderGroup;
    @FXML private RadioButton maleRadio;
    @FXML private RadioButton femaleRadio;
    @FXML private javafx.scene.layout.VBox passwordBox;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label formErrorLabel;

    private final ObservableList<Member> masterData = FXCollections.observableArrayList();
    private FilteredList<Member> filteredData;

    @FXML
    public void initialize() {
        departmentChoiceBox.setItems(FXCollections.observableArrayList(
                "CSE", "EEE", "ECE", "CE", "ME", "BECM", "URP", "ESE",
                "TE", "LE", "IEM", "BME", "Arch", "ChE"));

        studentIdColumn.setCellValueFactory(new PropertyValueFactory<>("studentId"));
        nameColumn.setCellValueFactory(new PropertyValueFactory<>("name"));
        emailColumn.setCellValueFactory(new PropertyValueFactory<>("email"));
        departmentColumn.setCellValueFactory(new PropertyValueFactory<>("department"));
        phoneColumn.setCellValueFactory(new PropertyValueFactory<>("phone"));
        genderColumn.setCellValueFactory(new PropertyValueFactory<>("gender"));

        filteredData = new FilteredList<>(masterData, m -> true);
        memberTable.setItems(filteredData);
        // UNCONSTRAINED policy: the columns keep their readable widths and the
        // table scrolls horizontally when it is too narrow (see members.fxml).
        memberTable.setColumnResizePolicy(TableView.UNCONSTRAINED_RESIZE_POLICY);

        searchField.textProperty().addListener((obs, oldVal, newVal) -> applySearchFilter(newVal));

        memberTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSel, newSel) -> {
            if (newSel != null) {
                populateForm(newSel);
                setEditMode(true);
            }
        });

        setEditMode(false);
        loadMembersFromDb();
    }

    private void applySearchFilter(String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        filteredData.setPredicate(member -> {
            if (q.isEmpty()) return true;
            String sid = member.getStudentId() != null ? member.getStudentId().toLowerCase() : "";
            String name = member.getName() != null ? member.getName().toLowerCase() : "";
            String email = member.getEmail() != null ? member.getEmail().toLowerCase() : "";
            return sid.contains(q) || name.contains(q) || email.contains(q);
        });
    }

    private void loadMembersFromDb() {
        // Background fetch (RefreshQueue): the query never blocks the JavaFX
        // thread, and the new rows replace the old ones in one FX-thread step.
        RefreshQueue.request(() -> {
            List<Member> loaded = new ArrayList<>();
            String failure = null;

            try {
                // Interface-typed reference: any LibraryRepository<Member> fits here.
                LibraryRepository<Member> repository = new MemberRepository();
                loaded.addAll(repository.findAll());
            } catch (SQLException e) {
                e.printStackTrace();
                failure = e.getMessage();
            }

            final String error = failure;
            AppExecutors.runFx(() -> {
                masterData.setAll(loaded); // empty on error, same as before
                if (error != null) {
                    FormMessage.error(formErrorLabel, "Failed to load members: " + error);
                }
            });
        });
    }

    private void populateForm(Member member) {
        studentIdField.setText(member.getStudentId());
        nameField.setText(member.getName());
        emailField.setText(member.getEmail());
        departmentChoiceBox.setValue(member.getDepartment());
        phoneField.setText(member.getPhone());

        // Enum instead of comparing raw "Male"/"Female" strings.
        Gender gender = Gender.fromLabel(member.getGender());
        if (gender == Gender.MALE) {
            maleRadio.setSelected(true);
        } else if (gender == Gender.FEMALE) {
            femaleRadio.setSelected(true);
        } else {
            genderGroup.selectToggle(null);
        }

        FormMessage.clear(formErrorLabel);
    }

    /** Editing an existing member locks the Student ID (primary key) and hides the password fields,
     *  since login credentials are only set at registration time (Step 10 scope), not edited here. */
    private void setEditMode(boolean editing) {
        studentIdField.setDisable(editing);
        passwordBox.setVisible(!editing);
        passwordBox.setManaged(!editing);
    }

    @FXML
    private void handleAdd() {
        FormMessage.clear(formErrorLabel);

        String studentId = trimToNull(studentIdField.getText());
        String name = trimToNull(nameField.getText());
        String email = trimToNull(emailField.getText());
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (studentId == null || name == null || email == null) {
            FormMessage.error(formErrorLabel, "Student ID, Name and Email are required.");
            return;
        }
        if (password == null || password.isBlank()) {
            FormMessage.error(formErrorLabel, "A password is required to register a new member's login.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            FormMessage.error(formErrorLabel, "Password and Confirm Password do not match.");
            return;
        }

        String department = departmentChoiceBox.getValue();
        String phone = trimToNull(phoneField.getText());
        Gender gender = selectedGender();
        final String genderValue = gender == null ? null : gender.label();

        // Registration writes to two stores: the SQLite profile and the online
        // JSON on JSONBin.io. The second one is an HTTP round trip, so both
        // happen on the pool and only the outcome is pushed back to the
        // JavaFX thread - the window keeps painting while it waits.
        AppExecutors.execute(() -> {
            String failure = insertMember(studentId, name, email, department, phone, genderValue);

            if (failure == null) {
                try {
                    boolean registered = JsonAuthService.registerStudent(studentId, email, password);
                    if (!registered) {
                        // Roll back the SQLite row so we don't leave an orphaned profile with no login.
                        deleteMemberRowQuietly(studentId);
                        failure = "That Student ID or Email already has login credentials.";
                    }
                } catch (JsonBinException e) {
                    // Online registration failed (no network, bad bin id, invalid JSON...):
                    // roll back the SQLite row so profile and login stay in step.
                    deleteMemberRowQuietly(studentId);
                    failure = e.getMessage();
                }
            }

            final String error = failure;
            AppExecutors.runFx(() -> {
                if (error != null) {
                    FormMessage.error(formErrorLabel, error);
                    return;
                }
                loadMembersFromDb();
                handleClear();
                FormMessage.success(formErrorLabel, "Member registered.");
            });
        });
    }

    /**
     * Writes the profile row. Runs on the background worker (called from
     * handleAdd) and returns null on success or the message to show on
     * failure - same wording as before the online registration existed.
     */
    private String insertMember(String studentId, String name, String email,
                                String department, String phone, String gender) {
        String insertSql = "INSERT INTO members (student_id, name, email, department, phone, gender) " +
                "VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(insertSql)) {
            ps.setString(1, studentId);
            ps.setString(2, name);
            ps.setString(3, email);
            ps.setString(4, department);
            ps.setString(5, phone);
            ps.setString(6, gender);
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to save profile (Student ID or Email may already be in use): "
                    + e.getMessage();
        }
    }

    /**
     * Deletes a member row without reporting anything - used only to roll back
     * a registration whose online half failed. The rollback is best effort by
     * nature, so there is no message worth showing.
     */
    private static void deleteMemberRowQuietly(String studentId) {
        deleteMemberRow(studentId);
    }

    @FXML
    private void handleUpdate() {
        FormMessage.clear(formErrorLabel);
        Member selected = memberTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            FormMessage.error(formErrorLabel, "Select a member in the table first.");
            return;
        }

        String name = trimToNull(nameField.getText());
        String email = trimToNull(emailField.getText());
        if (name == null || email == null) {
            FormMessage.error(formErrorLabel, "Name and Email are required.");
            return;
        }

        String department = departmentChoiceBox.getValue();
        String phone = trimToNull(phoneField.getText());
        String genderValue = labelOf(selectedGender());
        String studentId = selected.getStudentId();

        AppExecutors.execute(() -> {
            String failure = updateMember(studentId, name, email, department, phone, genderValue);

            final String error = failure;
            AppExecutors.runFx(() -> {
                loadMembersFromDb();
                if (error != null) {
                    FormMessage.error(formErrorLabel, error);
                    return;
                }
                handleClear();
                FormMessage.success(formErrorLabel, "Member updated.");
            });
        });
    }

    /** The label a {@link Gender} is stored under, or null when none is chosen. */
    private static String labelOf(Gender gender) {
        return gender == null ? null : gender.label();
    }

    @FXML
    private void handleDelete() {
        FormMessage.clear(formErrorLabel);
        Member selected = memberTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            FormMessage.error(formErrorLabel, "Select a member in the table first.");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete member \"" + selected.getName() + "\" (" + selected.getStudentId() + ")?\n"
                        + "Their login credentials in the online JSONBin.io store will NOT be removed automatically.");
        confirm.setHeaderText(null);
        if (confirm.showAndWait().filter(bt -> bt == ButtonType.OK).isEmpty()) {
            return;
        }

        String studentId = selected.getStudentId();

        AppExecutors.execute(() -> {
            // Both checks are SQL, so they belong off the FX thread, and both
            // have to happen after the dialog: confirming takes time, and the
            // database may have changed while it was open.
            String failure;
            if (hasActiveBorrows(studentId)) {
                failure = "This member currently has borrowed book(s). "
                        + "Return them before deleting the member.";
            } else if (hasBorrowHistory(studentId)) {
                failure = "This member has borrowing history, so the member cannot be deleted "
                        + "while those borrow records exist.";
            } else {
                failure = deleteMemberRow(studentId);
            }

            final String error = failure;
            AppExecutors.runFx(() -> {
                loadMembersFromDb();
                if (error != null) {
                    FormMessage.error(formErrorLabel, error);
                    return;
                }
                handleClear();
                FormMessage.success(formErrorLabel, "Member deleted.");
            });
        });
    }

    /**
     * True while the member still has at least one book out.
     */
    private static boolean hasActiveBorrows(String studentId) {
        return countBorrows(studentId, " AND status = 'BORROWED'") > 0;
    }

    /**
     * True when any borrow record (borrowed or returned) points at this member.
     *
     * borrow_records.student_id is a real foreign key into members.student_id,
     * so SQLite would refuse the DELETE anyway. Checking first turns that raw
     * constraint error into an explanation the admin can act on.
     *
     * <p>A failure is reported as false, which lets the DELETE attempt through
     * and surfaces the database's own error instead of hiding it behind a
     * misleading message.
     */
    private static boolean hasBorrowHistory(String studentId) {
        return countBorrows(studentId, "") > 0;
    }

    /**
     * Counts a member's borrow records, optionally restricted to the open ones.
     *
     * <p>A failure is reported as zero, so a database problem surfaces through
     * the write that follows rather than being swallowed here.
     */
    private static int countBorrows(String studentId, String extraWhere) {
        String sql = "SELECT COUNT(*) FROM borrow_records WHERE student_id = ?" + extraWhere;
        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, studentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Updates a member's editable columns.
     *
     * The studentId and the online credentials are deliberately not among
     * them: the id is the key the online login is matched on, and passwords
     * are changed from the student's own Change Password page.
     *
     * @return null on success, or the message to show on failure
     */
    private static String updateMember(String studentId, String name, String email,
                                       String department, String phone, String gender) {
        String sql = "UPDATE members SET name = ?, email = ?, department = ?, phone = ?, gender = ? "
                + "WHERE student_id = ?";
        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, email);
            ps.setString(3, department);
            ps.setString(4, phone);
            ps.setString(5, gender);
            ps.setString(6, studentId);
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to update member: " + e.getMessage();
        }
    }

    /**
     * Deletes a member row.
     *
     * @return null on success, or the message to show on failure
     */
    private static String deleteMemberRow(String studentId) {
        try (Connection conn = SQLiteConnection.connect();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM members WHERE student_id = ?")) {
            ps.setString(1, studentId);
            ps.executeUpdate();
            return null;
        } catch (SQLException e) {
            e.printStackTrace();
            return "Failed to delete member: " + e.getMessage();
        }
    }

    @FXML
    private void handleClear() {
        memberTable.getSelectionModel().clearSelection();
        studentIdField.clear();
        nameField.clear();
        emailField.clear();
        departmentChoiceBox.setValue(null);
        phoneField.clear();
        genderGroup.selectToggle(null);
        passwordField.clear();
        confirmPasswordField.clear();
        FormMessage.clear(formErrorLabel);
        setEditMode(false);
    }

    /** The radio buttons speak {@link Gender}; only the label reaches the SQL. */
    private Gender selectedGender() {
        Toggle selected = genderGroup.getSelectedToggle();
        if (selected == maleRadio) return Gender.MALE;
        if (selected == femaleRadio) return Gender.FEMALE;
        return null;
    }

    private String trimToNull(String value) {
        return (value == null || value.isBlank()) ? null : value.trim();
    }
}
