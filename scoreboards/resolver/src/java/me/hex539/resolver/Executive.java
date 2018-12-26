package me.hex539.resolver;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Predicate;

import edu.clics.proto.ClicsProto.*;

import me.hex539.contest.ApiDetective;
import me.hex539.contest.ContestConfig;
import me.hex539.contest.ContestDownloader;
import me.hex539.contest.ScoreboardModel;
import me.hex539.contest.ScoreboardModelImpl;
import me.hex539.contest.ResolverController;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.system.MemoryUtil.*;

public class Executive {
  public static void main(String[] args) throws Exception {
    Invocation invocation = Invocation.parseFrom(args);

    final ContestConfig.Source source = getSource(invocation).orElseGet(() -> {
      System.exit(1);
      return null;
    });

    final CompletableFuture<ByteBuffer> fontData = CompletableFuture.supplyAsync(
        () -> FontRenderer.mapResource(FontRenderer.FONT_UNIFONT));

    final CompletableFuture<ClicsContest> entireContest =
        CompletableFuture.supplyAsync(() -> {
          try {
            return new ContestDownloader(source).fetch();
          } catch (Exception e) {
            throw new CompletionException(e);
          }
        });

    final CompletableFuture<ScoreboardModel> reference = entireContest
        .thenApplyAsync(ScoreboardModelImpl::newBuilder)
        .thenApplyAsync(b -> b
            .filterTooLateSubmissions()
            .filterGroups(getGroupFilter(invocation))
            .build()
            .immutable());

    final CompletableFuture<ScoreboardModelImpl> model = entireContest
        .thenCombineAsync(reference, ScoreboardModelImpl::newBuilder)
        .thenApplyAsync(b -> b
            .withEmptyScoreboard()
            .filterSubmissions(s -> false)
            .build());

    final CompletableFuture<ResolverController> resolver = entireContest
        .thenCombineAsync(reference, ResolverController::new)
        .thenCombineAsync(model, ResolverController::addObserver);

    new ResolverWindow(resolver, model, fontData).run();
    System.exit(0);
  }

  private static Optional<ContestConfig.Source> getSource(Invocation invocation) {
    final ContestConfig.Source source;
    if (invocation.getUrl() != null) {
      ContestConfig.Source.Builder sourceBuilder =
          ApiDetective.detectApi(invocation.getUrl()).get()
              .toBuilder();
      if (invocation.getUsername() != null) {
          sourceBuilder.setAuthentication(
              ContestConfig.Authentication.newBuilder()
                  .setHttpUsername(invocation.getUsername())
                  .setHttpPassword(invocation.getPassword())
                  .build());
      }
      return Optional.ofNullable(sourceBuilder.build());
    } else if (invocation.getFile() != null) {
      return Optional.ofNullable(
          ContestConfig.Source.newBuilder()
              .setFilePath(invocation.getFile())
              .build());
    } else {
      System.err.println("Need one of --url or --path to load a contest.");
      return Optional.empty();
    }
  }

  private static Predicate<Group> getGroupFilter(Invocation invocation) {
    final Set<String> groups = invocation.getGroups() != null
        ? new HashSet<>(Arrays.asList(invocation.getGroups().split(",")))
        : null;
    return groups != null
        ? g -> groups.contains(g.getName()) || groups.contains(g.getId())
        : g -> !g.getHidden();
  }
}
