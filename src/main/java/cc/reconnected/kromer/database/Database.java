package cc.reconnected.kromer.database;

import cc.reconnected.kromer.responses.Transaction;
import com.google.gson.Gson;
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
                            password TEXT,
                            outgoingNotSeen TEXT,
                            incomingNotSeen TEXT
                        )
                    """);
            statement.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void setWallet(UUID playerUuid, Wallet wallet) {
        try {
            if (this.getWallet(playerUuid) == null) {

                PreparedStatement stmt = connection.prepareStatement("""
                            INSERT OR REPLACE INTO wallets (address, uuid, password, incomingNotSeen, outgoingNotSeen)
                            VALUES (?, ?, ?, ?, ?)
                        """);
                stmt.setString(1, wallet.address);
                stmt.setString(2, playerUuid.toString());
                stmt.setString(3, wallet.password);
                stmt.setString(4, new Gson().toJson(wallet.incomingNotSeen, Transaction[].class));
                stmt.setString(5, new Gson().toJson(wallet.outgoingNotSeen, Transaction[].class));

                stmt.executeUpdate();
                stmt.close();
            } else {
                PreparedStatement stmt = connection.prepareStatement("""
                            UPDATE wallets
                            SET address = ?, password = ?, incomingNotSeen = ?, outgoingNotSeen = ?
                            WHERE uuid = ?
                        """);
                stmt.setString(1, wallet.address);
                stmt.setString(2, wallet.password);
                stmt.setString(3, new Gson().toJson(wallet.incomingNotSeen, Transaction[].class));
                stmt.setString(4, new Gson().toJson(wallet.outgoingNotSeen, Transaction[].class));
                stmt.setString(5, playerUuid.toString());
                stmt.executeUpdate();
                stmt.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public Wallet getWallet(UUID playerUuid) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                        SELECT address, password, incomingNotSeen, outgoingNotSeen FROM wallets WHERE uuid = ?
                    """);
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                return new Wallet(
                        rs.getString("address"),
                        rs.getString("password"),
                        new Gson().fromJson(rs.getString("incomingNotSeen"), Transaction[].class),
                        new Gson().fromJson(rs.getString("outgoingNotSeen"), Transaction[].class)
                );
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    public Pair<UUID, Wallet> getWallet(String address) {
        try {
            PreparedStatement stmt = connection.prepareStatement("""
                        SELECT uuid, password, incomingNotSeen, outgoingNotSeen FROM wallets WHERE address = ?
                    """);
            stmt.setString(1, address);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                UUID uuid = UUID.fromString(rs.getString("uuid"));
                Wallet wallet = new Wallet(
                        address,
                        rs.getString("password"),
                        new Gson().fromJson(rs.getString("incomingNotSeen"), Transaction[].class),
                        new Gson().fromJson(rs.getString("outgoingNotSeen"), Transaction[].class)

                );
                return new Pair<>(uuid, wallet);
            }
        } catch (SQLException | IllegalArgumentException e) {
            e.printStackTrace();
        }
        return null;
    }
}