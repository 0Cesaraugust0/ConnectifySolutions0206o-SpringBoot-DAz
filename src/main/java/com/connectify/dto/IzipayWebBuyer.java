package com.connectify.dto;

import java.util.LinkedHashMap;
import java.util.Map;

public record IzipayWebBuyer(String firstName, String lastName, String dni, String phone,
                             String street, String city, String state, String postalCode) {
    public Map<String, String> billing(String email) {
        Map<String, String> value = new LinkedHashMap<>();
        value.put("firstName", name(firstName, "nombres"));
        value.put("lastName", name(lastName, "apellidos"));
        String document = required(dni, "DNI", 8);
        if (!document.matches("[0-9]{8}")) throw new IllegalArgumentException("Ingresa un DNI de 8 dígitos.");
        String mobile = required(phone, "teléfono", 15);
        if (!mobile.matches("[0-9]{7,15}")) throw new IllegalArgumentException("El teléfono debe contener entre 7 y 15 dígitos.");
        value.put("email", required(email, "correo", 50));
        value.put("phoneNumber", mobile);
        value.put("documentType", "DNI");
        value.put("document", document);
        value.put("street", bounded(street, "dirección", 5, 40));
        value.put("city", bounded(city, "ciudad", 3, 25));
        value.put("state", bounded(state, "departamento", 3, 25));
        value.put("country", "PE");
        String postal = required(postalCode, "código postal", 10);
        if (!postal.matches("[0-9]{5,10}")) throw new IllegalArgumentException("El código postal debe contener entre 5 y 10 dígitos.");
        value.put("postalCode", postal);
        return value;
    }

    private String name(String value, String label) {
        String text = bounded(value, label, 2, 50);
        if (!text.matches("[\\p{L} ]+")) throw new IllegalArgumentException("Usa solo letras y espacios en " + label + ".");
        return text;
    }

    private String bounded(String value, String label, int min, int max) {
        String text = required(value, label, max);
        if (text.length() < min) throw new IllegalArgumentException("Completa correctamente " + label + ".");
        return text;
    }

    private String required(String value, String label, int max) {
        if (value == null || value.isBlank() || value.trim().length() > max)
            throw new IllegalArgumentException("Completa correctamente " + label + ".");
        return value.trim();
    }
}
