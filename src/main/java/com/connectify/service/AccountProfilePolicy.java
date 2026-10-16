package com.connectify.service;

import com.connectify.entity.Role;
import com.connectify.entity.UserAccount;
import java.util.ArrayList;
import java.util.List;

public final class AccountProfilePolicy {
    private AccountProfilePolicy() { }

    public static List<String> missingFields(UserAccount account) {
        List<String> missing = new ArrayList<>();
        if (clean(account.getFirstName()).length() < 2) missing.add("Nombres");
        if (clean(account.getPaternalLastName()).length() < 2) missing.add("Apellido paterno");
        if (!clean(account.getEmail()).matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) missing.add("Correo válido");
        if (!clean(account.getDni()).matches("\\d{8}")) missing.add("DNI de 8 dígitos");
        if (!clean(account.getPhone()).replaceAll("[\\s()+-]", "").matches("\\d{9,15}")) missing.add("Teléfono");
        if (account.getRole() == Role.ORGANIZER) {
            if (clean(account.getCompanyName()).length() < 3) missing.add("Nombre comercial / razón social");
            if (!clean(account.getRuc()).matches("\\d{11}")) missing.add("RUC de 11 dígitos");
        }
        return List.copyOf(missing);
    }

    private static String clean(String value) { return value == null ? "" : value.trim(); }
}
