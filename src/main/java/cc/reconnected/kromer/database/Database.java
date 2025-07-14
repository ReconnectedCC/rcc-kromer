package cc.reconnected.kromer.database;

import net.minecraft.util.Pair;

import java.sql.*;
import java.util.UUID;

public class Database {
    private Connection connection;

    public Database() {
        try {
            connection = DriverManager.getConnection("jdbc:sqlite:rcc-kromer.db");

            Statement statement = connection.createStatement();
            statement.executeUpdate("""
                CREATE TABLE IF NOT EXISTS wallets (
                    address TEXT PRIMARY KEY,
                    uuid TEXT,
                    password TEXT
                )
            """);
            statement.close();
        } catch (SQLException e) {
            // Fail silently, or log if needed
        }
    }

    public void setWallet(UUID playerUuid, Wallet wallet) {

        try {
            if (this.getWallet(playerUuid) == null) {

                PreparedStatement stmt = connection.prepareStatement("""
                            INSERT OR REPLACE INTO wallets (address, uuid, password)
                            VALUES (?, ?, ?)
                        """);
                stmt.setString(1, wallet.address);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, wallet.password);
                stmt.executeUpdate();
                stmt.close();
            } else {
                PreparedStatement stmt = connection.prepareStatement("""
                    UPDATE wallets
                    SET address = ?, password = ?
                    WHERE uuid = ?
                """);
                stmt.setString(1, wallet.address);
                stmt.setString(2, wallet.password);
                stmt.setString(3, playerUuid.toString());
                stmt.executeUpdate();
                stmt.close();
            }
        } catch (SQLException e) {
            // Fail silently, return nothing
        }
    }

    public Wallet getWallet(UUID playerUuid) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT address, password FROM wallets WHERE uuid = ?
            """);
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Wallet(rs.getString("address"), rs.getString("password"));
            }
        } catch (SQLException ignored) {}
        return null;
    }

    public Pair<UUID, Wallet> getWallet(String address) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                SELECT uuid, password FROM wallets WHERE address = ?
            """);
            stmt.setString(1, address);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                Wallet wallet = new Wallet(address, rs.getString("password"));
                return new Pair<>(uuid, wallet);
            }
        } catch (SQLException | IllegalArgumentException ignored) {}
        return null;
    }
}