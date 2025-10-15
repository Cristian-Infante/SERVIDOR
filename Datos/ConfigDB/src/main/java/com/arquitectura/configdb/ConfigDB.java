package com.arquitectura.configdb;

public final class ConfigDB {

    private ConfigDB() {
    }

    public static void main(String[] args) {
        System.out.println("URL MySQL: " + DBConfig.require("mysql.url"));
        System.out.println("Puerto servidor: " + DBConfig.require("server.port"));
    }
}
