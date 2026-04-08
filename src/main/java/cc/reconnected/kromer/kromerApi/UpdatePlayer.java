package cc.reconnected.kromer.kromerApi;

import ovh.sad.jkromer.Errors;
import ovh.sad.jkromer.http.HttpEndpoint;
import ovh.sad.jkromer.http.Result;
import ovh.sad.jkromer.jKromer;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

// TODO: Merge with JKromer
public class UpdatePlayer extends HttpEndpoint {
    public UpdatePlayer() {
    }

    public static CompletableFuture<Result<UpdatePlayerResponse>> execute(String kromerKey, UUID uuid, String username) {
        try {
            String requestBody = gson.toJson(new UpdatePlayerRequest(uuid, username));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(jKromer.endpoint_raw + "/api/_internal/wallet/update-player"))
                    .header("Content-Type", "application/json")
                    .header("Kromer-Key", kromerKey)
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            return http
                    .sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply((response) -> {
                        if (response.statusCode() == 200) {
                            return new Result.Ok<>(new UpdatePlayerResponse());
                        }

                        return (Result<UpdatePlayerResponse>) new Result.Err<UpdatePlayerResponse>(Errors.internal_problem.toResponse("Internal API request could not be sent. Status code: " + response.statusCode()));
                    })
                    .exceptionally(ex -> new Result.Err<>(Errors.internal_problem.toResponse("HTTP error: " + ex.getMessage())));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(new Result.Err<>(Errors.internal_problem.toResponse("Failed to build request: " + e.getMessage())));
        }
    }

    private record UpdatePlayerRequest(UUID uuid, String username) {
    }

    // Here for the sake of it
    public record UpdatePlayerResponse() {
    }
}