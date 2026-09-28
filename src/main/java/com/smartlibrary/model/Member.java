package com.smartlibrary.model;

import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

/**
 * Represents one row of the "members" table (a registered student).
 */
public class Member {

    private final SimpleIntegerProperty id;
    private final StringProperty studentId;
    private final StringProperty name;
    private final StringProperty email;
    private final StringProperty department;
    private final StringProperty phone;
    private final StringProperty gender; // "Male" or "Female"; added in Step 10

    public Member(int id, String studentId, String name, String email,
                  String department, String phone, String gender) {
        this.id = new SimpleIntegerProperty(id);
        this.studentId = new SimpleStringProperty(studentId);
        this.name = new SimpleStringProperty(name);
        this.email = new SimpleStringProperty(email);
        this.department = new SimpleStringProperty(department);
        this.phone = new SimpleStringProperty(phone);
        this.gender = new SimpleStringProperty(gender);
    }

    public int getId() { return id.get(); }
    public void setId(int value) { id.set(value); }

    public String getStudentId() { return studentId.get(); }
    public void setStudentId(String value) { studentId.set(value); }
    public StringProperty studentIdProperty() { return studentId; }

    public String getName() { return name.get(); }
    public void setName(String value) { name.set(value); }
    public StringProperty nameProperty() { return name; }

    public String getEmail() { return email.get(); }
    public void setEmail(String value) { email.set(value); }
    public StringProperty emailProperty() { return email; }

    public String getDepartment() { return department.get(); }
    public void setDepartment(String value) { department.set(value); }
    public StringProperty departmentProperty() { return department; }

    public String getPhone() { return phone.get(); }
    public void setPhone(String value) { phone.set(value); }
    public StringProperty phoneProperty() { return phone; }

    public String getGender() { return gender.get(); }
    public void setGender(String value) { gender.set(value); }
    public StringProperty genderProperty() { return gender; }

    @Override
    public String toString() {
        return name.get() + " (" + studentId.get() + ")";
    }
}
