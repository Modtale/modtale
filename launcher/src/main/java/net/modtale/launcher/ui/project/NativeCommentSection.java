package net.modtale.launcher.ui.project;

import static net.modtale.launcher.ui.browse.card.ProjectCardFormatter.timeAgo;
import static net.modtale.launcher.ui.common.LauncherUi.primaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.secondaryButton;
import static net.modtale.launcher.ui.common.LauncherUi.value;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import net.modtale.launcher.model.project.ProjectComment;
import net.modtale.launcher.model.project.ProjectDetail;
import net.modtale.launcher.model.project.ProjectSummary;
import net.modtale.launcher.model.user.CurrentUser;
import net.modtale.launcher.model.user.UserSummary;
import net.modtale.launcher.ui.common.CachedImageLoader;
import net.modtale.launcher.ui.common.LauncherIcons;

final class NativeCommentSection {

    private static final String OWNER_ROLE_COLOR = "#f97316";
    private static final PseudoClass UPVOTED = PseudoClass.getPseudoClass("upvoted");
    private static final PseudoClass DOWNVOTED = PseudoClass.getPseudoClass("downvoted");

    interface Actions {
        void submitComment(String editingCommentId, String content);

        void deleteComment(ProjectComment comment);

        void submitReply(String commentId, String content);

        void vote(String commentId, boolean reply, boolean upvote);

        void report(String commentId);
    }

    private final CachedImageLoader imageLoader;
    private final NativeMarkdownRenderer markdownRenderer;
    private final Supplier<CurrentUser> currentUser;
    private final Runnable signIn;
    private final BiConsumer<String, String> openProfile;
    private final Actions actions;
    private final Runnable requestRender;

    private String composerText = "";
    private String editingCommentId;
    private String replyingCommentId;
    private String replyText = "";

    NativeCommentSection(
            CachedImageLoader imageLoader,
            NativeMarkdownRenderer markdownRenderer,
            Supplier<CurrentUser> currentUser,
            Runnable signIn,
            BiConsumer<String, String> openProfile,
            Actions actions,
            Runnable requestRender
    ) {
        this.imageLoader = imageLoader;
        this.markdownRenderer = markdownRenderer;
        this.currentUser = currentUser == null ? () -> null : currentUser;
        this.signIn = signIn == null ? () -> {
        } : signIn;
        this.openProfile = openProfile == null ? (id, username) -> {
        } : openProfile;
        this.actions = actions;
        this.requestRender = requestRender == null ? () -> {
        } : requestRender;
    }

    Node render(
            ProjectSummary summary,
            ProjectDetail detail,
            List<ProjectComment> comments,
            Map<String, UserSummary> userProfiles,
            boolean loading,
            boolean submitting
    ) {
        CurrentUser user = currentUser.get();
        boolean creator = isCreator(user, summary, detail);
        boolean disabled = detail != null && Boolean.FALSE.equals(detail.allowComments());
        if (disabled && !creator) {
            return null;
        }

        List<ProjectComment> safeComments = comments == null ? List.of() : comments;
        Map<String, UserSummary> safeProfiles = userProfiles == null ? Map.of() : userProfiles;

        VBox section = new VBox(0);
        section.setId("comments");
        section.getStyleClass().add("project-comments-section");
        section.setMaxWidth(Double.MAX_VALUE);

        HBox heading = new HBox(12);
        heading.getStyleClass().add("project-comments-heading");
        heading.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(safeComments.size() + " Comments");
        title.getStyleClass().add("project-comments-title");
        heading.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.MESSAGE_SQUARE, 24), title);
        section.getChildren().add(heading);

        if (disabled) {
            section.getChildren().add(disabledNotice());
        }

        section.getChildren().add(user == null ? signInPrompt() : composer(user, submitting));

        VBox list = new VBox(16);
        list.getStyleClass().add("project-comments-list");
        if (loading) {
            list.getChildren().add(stateCard(NativeSpinner.inline(20)));
        } else if (safeComments.isEmpty()) {
            list.getChildren().add(stateCard("No comments yet. Be the first to share your thoughts!"));
        } else {
            for (ProjectComment comment : safeComments) {
                list.getChildren().add(commentCard(comment, summary, detail, safeProfiles, creator, submitting));
            }
        }
        section.getChildren().add(list);
        return section;
    }

    void clearComposer() {
        composerText = "";
        editingCommentId = null;
    }

    void clearReply() {
        replyText = "";
        replyingCommentId = null;
    }

    void clearState() {
        clearComposer();
        clearReply();
    }

    private Node disabledNotice() {
        HBox notice = new HBox(8);
        notice.getStyleClass().add("project-comments-disabled-notice");
        notice.setAlignment(Pos.CENTER_LEFT);
        Label label = new Label("Comments are currently disabled. Only you can see them.");
        label.getStyleClass().add("project-comments-disabled-text");
        notice.getChildren().addAll(LauncherIcons.icon(LauncherIcons.Glyph.FLAG, 16), label);
        return notice;
    }

    private Node signInPrompt() {
        Button signInButton = new Button("Log in to join the conversation.");
        signInButton.getStyleClass().add("project-comments-signin");
        signInButton.setMaxWidth(Double.MAX_VALUE);
        signInButton.setOnAction(event -> signIn.run());
        return signInButton;
    }

    private Node composer(CurrentUser user, boolean submitting) {
        VBox shell = new VBox(14);
        shell.getStyleClass().add("project-comments-composer");
        shell.setMaxWidth(Double.MAX_VALUE);

        HBox top = new HBox(12);
        top.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(editingCommentId == null ? "Leave a comment" : "Edit your comment");
        title.getStyleClass().add("project-comments-composer-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(title, spacer);
        if (editingCommentId != null) {
            Button cancel = new Button("Cancel");
            cancel.getStyleClass().add("project-comments-text-action-danger");
            cancel.setOnAction(event -> {
                clearComposer();
                requestRender.run();
            });
            top.getChildren().add(cancel);
        }

        HBox editRow = new HBox(12);
        editRow.setAlignment(Pos.TOP_LEFT);
        TextArea text = new TextArea(composerText);
        text.getStyleClass().add("project-comments-textarea");
        text.setPromptText("What are your thoughts?");
        text.setWrapText(true);
        text.setPrefRowCount(3);
        text.setMinHeight(72);
        text.setDisable(submitting);
        text.textProperty().addListener((observable, previous, value) -> composerText = value == null ? "" : value);
        HBox.setHgrow(text, Priority.ALWAYS);
        editRow.getChildren().addAll(avatar(36, user.id(), user.username(), user.avatarUrl(), false), text);

        HBox footer = new HBox();
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button submit = primaryButton(editingCommentId == null ? "Post Comment" : "Update");
        submit.getStyleClass().add("project-comments-submit");
        submit.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.SEND, 14));
        submit.setDisable(submitting || composerText.isBlank());
        submit.setOnAction(event -> actions.submitComment(editingCommentId, composerText));
        footer.getChildren().add(submit);

        shell.getChildren().addAll(top, editRow, footer);
        return shell;
    }

    private Node commentCard(
            ProjectComment comment,
            ProjectSummary summary,
            ProjectDetail detail,
            Map<String, UserSummary> profiles,
            boolean creator,
            boolean submitting
    ) {
        CurrentUser user = currentUser.get();
        String userId = value(comment.userId(), "");
        CommentIdentity identity = identity(userId, comment.user(), comment.author(), profiles, comment.date());
        boolean owner = user != null && (value(user.id(), "").equals(userId)
                || value(user.username(), "").equalsIgnoreCase(identity.username()));

        HBox card = new HBox(16);
        card.getStyleClass().add("project-comment-card");
        card.setAlignment(Pos.TOP_LEFT);
        card.getChildren().add(voteWidget(comment.id(), false, comment.score(), comment.userVoteFor(user == null ? null : user.id()), submitting));

        VBox body = new VBox(0);
        body.setMinWidth(0);
        HBox.setHgrow(body, Priority.ALWAYS);
        body.getChildren().add(commentHeader(identity, roleBadge(userId, summary, detail), 40));
        Node markdown = markdown(comment.content());
        VBox.setMargin(markdown, new Insets(8, 0, 0, 0));
        body.getChildren().add(markdown);
        body.getChildren().add(actionsRow(comment, creator, owner));

        if (replyingCommentId != null && replyingCommentId.equals(comment.id())) {
            body.getChildren().add(replyForm(comment.id(), submitting));
        } else if (comment.developerReply() != null) {
            body.getChildren().add(developerReply(comment, profiles, summary, detail, submitting));
        }

        card.getChildren().add(body);
        return card;
    }

    private Node commentHeader(CommentIdentity identity, RoleBadge roleBadge, double avatarSize) {
        HBox header = new HBox(12);
        header.getStyleClass().add("project-comment-header");
        header.setAlignment(Pos.CENTER_LEFT);
        Node avatar = avatar(avatarSize, identity.userId(), identity.username(), identity.avatarUrl(), true);
        VBox meta = new VBox(2);
        meta.getStyleClass().add("project-comment-meta");
        Label name = new Label(identity.username());
        name.getStyleClass().add("project-comment-author");
        if (!identity.userId().isBlank()) {
            name.setCursor(Cursor.HAND);
            name.setOnMouseClicked(event -> openProfile.accept(identity.userId(), identity.username()));
        }
        meta.getChildren().add(name);
        if (roleBadge != null) {
            meta.getChildren().add(roleBadge(roleBadge));
        }
        Label date = new Label(timeAgo(identity.date()));
        date.getStyleClass().add("project-comment-date");
        meta.getChildren().add(date);
        header.getChildren().addAll(avatar, meta);
        return header;
    }

    private Node actionsRow(ProjectComment comment, boolean creator, boolean owner) {
        HBox row = new HBox(16);
        row.getStyleClass().add("project-comment-actions");
        row.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(row, new Insets(12, 0, 0, 0));
        if (creator && comment.developerReply() == null) {
            row.getChildren().add(actionButton("Reply", LauncherIcons.Glyph.MESSAGE_SQUARE, () -> {
                replyingCommentId = comment.id();
                replyText = "";
                requestRender.run();
            }));
        }
        if (creator && comment.developerReply() != null) {
            row.getChildren().add(actionButton("Edit Reply", LauncherIcons.Glyph.MESSAGE_SQUARE, () -> {
                replyingCommentId = comment.id();
                replyText = value(comment.developerReply().content(), "");
                requestRender.run();
            }));
        }
        CurrentUser user = currentUser.get();
        if (user != null && !owner) {
            row.getChildren().add(actionButton("Report", LauncherIcons.Glyph.FLAG, () -> actions.report(comment.id())));
        }
        if (owner) {
            row.getChildren().add(actionButton("Edit", LauncherIcons.Glyph.EDIT, () -> {
                composerText = value(comment.content(), "");
                editingCommentId = comment.id();
                replyingCommentId = null;
                requestRender.run();
            }));
        }
        if (creator || owner) {
            Button delete = actionButton("Delete", LauncherIcons.Glyph.TRASH, () -> actions.deleteComment(comment));
            delete.getStyleClass().add("danger");
            row.getChildren().add(delete);
        }
        return row;
    }

    private Button actionButton(String text, LauncherIcons.Glyph glyph, Runnable action) {
        Button button = new Button(text, LauncherIcons.icon(glyph, 15));
        button.getStyleClass().add("project-comment-action");
        button.setOnAction(event -> action.run());
        return button;
    }

    private Node replyForm(String commentId, boolean submitting) {
        VBox form = new VBox(10);
        form.getStyleClass().add("project-comment-reply-form");
        VBox.setMargin(form, new Insets(16, 0, 0, 0));
        TextArea text = new TextArea(replyText);
        text.getStyleClass().add("project-comment-reply-textarea");
        text.setPromptText("Write a reply...");
        text.setWrapText(true);
        text.setPrefRowCount(3);
        text.setDisable(submitting);
        text.textProperty().addListener((observable, previous, value) -> replyText = value == null ? "" : value);
        HBox footer = new HBox(8);
        footer.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = secondaryButton("Cancel");
        cancel.getStyleClass().add("project-comment-reply-cancel");
        cancel.setOnAction(event -> {
            clearReply();
            requestRender.run();
        });
        Button submit = primaryButton("Post Reply");
        submit.getStyleClass().add("project-comment-reply-submit");
        submit.setGraphic(LauncherIcons.icon(LauncherIcons.Glyph.CORNER_DOWN_RIGHT, 15));
        submit.setDisable(submitting || replyText.isBlank());
        submit.setOnAction(event -> actions.submitReply(commentId, replyText));
        footer.getChildren().addAll(cancel, submit);
        form.getChildren().addAll(text, footer);
        return form;
    }

    private Node developerReply(
            ProjectComment comment,
            Map<String, UserSummary> profiles,
            ProjectSummary summary,
            ProjectDetail detail,
            boolean submitting
    ) {
        ProjectComment.Reply reply = comment.developerReply();
        String replyUserId = value(reply.userId(), "");
        CommentIdentity identity = identity(replyUserId, reply.user(), reply.author(), profiles, reply.date());

        HBox row = new HBox(12);
        row.getStyleClass().add("project-comment-developer-reply-row");
        VBox.setMargin(row, new Insets(12, 0, 0, 0));
        row.getChildren().add(replyConnector());
        row.getChildren().add(voteWidget(comment.id(), true, reply.score(), reply.userVoteFor(currentUserId()), submitting));

        VBox card = new VBox(0);
        card.getStyleClass().add("project-comment-developer-reply");
        card.setMinWidth(0);
        HBox.setHgrow(card, Priority.ALWAYS);
        card.getChildren().add(commentHeader(identity, roleBadge(replyUserId, summary, detail), 32));
        Node markdown = markdown(reply.content());
        VBox.setMargin(markdown, new Insets(8, 0, 0, 0));
        card.getChildren().add(markdown);

        HBox actionsRow = new HBox(16);
        actionsRow.getStyleClass().add("project-comment-actions");
        actionsRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setMargin(actionsRow, new Insets(12, 0, 0, 0));
        CurrentUser user = currentUser.get();
        if (user != null && !value(user.id(), "").equals(replyUserId)) {
            actionsRow.getChildren().add(actionButton("Report", LauncherIcons.Glyph.FLAG, () -> actions.report(comment.id())));
        }
        card.getChildren().add(actionsRow);
        row.getChildren().add(card);
        return row;
    }

    private Node replyConnector() {
        StackPane connector = new StackPane();
        connector.getStyleClass().add("project-comment-reply-connector");
        connector.setMinWidth(28);
        connector.setPrefWidth(28);
        connector.setMaxWidth(28);
        Region vertical = new Region();
        vertical.getStyleClass().add("project-comment-reply-line-vertical");
        Region horizontal = new Region();
        horizontal.getStyleClass().add("project-comment-reply-line-horizontal");
        StackPane.setAlignment(vertical, Pos.TOP_CENTER);
        StackPane.setAlignment(horizontal, Pos.TOP_RIGHT);
        StackPane.setMargin(horizontal, new Insets(17, 0, 0, 0));
        connector.getChildren().addAll(vertical, horizontal);
        return connector;
    }

    private Node voteWidget(String commentId, boolean reply, int score, String userVote, boolean submitting) {
        VBox vote = new VBox(0);
        vote.getStyleClass().add("project-comment-vote");
        vote.setAlignment(Pos.TOP_CENTER);
        Button up = voteButton(LauncherIcons.Glyph.ARROW_BIG_UP, () -> actions.vote(commentId, reply, true));
        up.pseudoClassStateChanged(UPVOTED, "up".equals(userVote));
        Button down = voteButton(LauncherIcons.Glyph.ARROW_BIG_DOWN, () -> actions.vote(commentId, reply, false));
        down.pseudoClassStateChanged(DOWNVOTED, "down".equals(userVote));
        up.setDisable(submitting);
        down.setDisable(submitting);
        Label scoreLabel = new Label(score > 0 ? "+" + score : Integer.toString(score));
        scoreLabel.getStyleClass().add("project-comment-score");
        if ("up".equals(userVote)) {
            scoreLabel.getStyleClass().add("upvoted");
        } else if ("down".equals(userVote)) {
            scoreLabel.getStyleClass().add("downvoted");
        }
        vote.getChildren().addAll(up, scoreLabel, down);
        return vote;
    }

    private Button voteButton(LauncherIcons.Glyph glyph, Runnable action) {
        Button button = new Button(null, LauncherIcons.icon(glyph, 24));
        button.getStyleClass().add("project-comment-vote-button");
        button.setOnAction(event -> {
            if (currentUser.get() == null) {
                signIn.run();
                return;
            }
            action.run();
        });
        return button;
    }

    private Node markdown(String content) {
        Node rendered = markdownRenderer.render(value(content, ""));
        rendered.getStyleClass().add("project-comment-markdown");
        return rendered;
    }

    private Node stateCard(String message) {
        Label state = new Label(message);
        state.getStyleClass().add("project-comments-state-label");
        return stateCard(state);
    }

    private Node stateCard(Node content) {
        StackPane state = new StackPane(content);
        state.getStyleClass().add("project-comments-state");
        state.setMaxWidth(Double.MAX_VALUE);
        return state;
    }

    private Node avatar(double size, String userId, String username, String avatarUrl, boolean clickable) {
        StackPane avatar = new StackPane();
        avatar.getStyleClass().add("project-comment-avatar");
        avatar.setMinSize(size, size);
        avatar.setPrefSize(size, size);
        avatar.setMaxSize(size, size);
        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size);
        clip.setArcHeight(size);
        avatar.setClip(clip);
        String resolvedAvatar = value(avatarUrl, "");
        if (!resolvedAvatar.isBlank() && !"null".equalsIgnoreCase(resolvedAvatar)) {
            ImageView image = new ImageView();
            image.getStyleClass().add("project-comment-avatar-image");
            image.setFitWidth(size);
            image.setFitHeight(size);
            image.setPreserveRatio(false);
            image.setSmooth(true);
            imageLoader.loadInto(image, resolvedAvatar, size * 2, size * 2);
            avatar.getChildren().add(image);
        } else {
            Label initial = new Label(initial(username));
            initial.getStyleClass().add("project-comment-avatar-initial");
            avatar.getChildren().add(initial);
        }
        if (clickable && !value(userId, "").isBlank()) {
            avatar.setCursor(Cursor.HAND);
            avatar.setOnMouseClicked(event -> openProfile.accept(userId, username));
        }
        return avatar;
    }

    private Node roleBadge(RoleBadge badge) {
        Label label = new Label(badge.label());
        label.getStyleClass().add("project-comment-role-badge");
        String color = badge.color();
        label.setStyle("-fx-text-fill: " + color + ";"
                + "-fx-background-color: " + color + "1A;"
                + "-fx-border-color: " + color + "33;");
        return label;
    }

    private RoleBadge roleBadge(String userId, ProjectSummary summary, ProjectDetail detail) {
        String authorId = value(detail == null ? null : detail.authorId(), summary == null ? null : summary.authorId());
        if (!value(userId, "").isBlank() && userId.equals(authorId)) {
            return new RoleBadge("Owner", OWNER_ROLE_COLOR);
        }
        return null;
    }

    private CommentIdentity identity(
            String userId,
            String username,
            ProjectComment.Author author,
            Map<String, UserSummary> profiles
    ) {
        return identity(userId, username, author, profiles, "");
    }

    private CommentIdentity identity(
            String userId,
            String username,
            ProjectComment.Author author,
            Map<String, UserSummary> profiles,
            String date
    ) {
        String id = value(userId, author == null ? "" : author.id());
        UserSummary profile = id.isBlank() ? null : profiles.get(id);
        String resolvedName = first(
                profile == null ? null : profile.username(),
                author == null ? null : author.username(),
                username,
                "Unknown"
        );
        String resolvedAvatar = first(profile == null ? null : profile.avatarUrl(), author == null ? null : author.avatarUrl(), "");
        return new CommentIdentity(id, resolvedName, resolvedAvatar, date);
    }

    private static boolean isCreator(CurrentUser user, ProjectSummary summary, ProjectDetail detail) {
        if (user == null) {
            return false;
        }
        String userId = value(user.id(), "");
        String userName = value(user.username(), "");
        String authorId = value(detail == null ? null : detail.authorId(), summary == null ? null : summary.authorId());
        String authorName = value(detail == null ? null : detail.author(), summary == null ? null : summary.author());
        return (!userId.isBlank() && userId.equals(authorId))
                || (!userName.isBlank() && userName.equalsIgnoreCase(authorName));
    }

    private String currentUserId() {
        CurrentUser user = currentUser.get();
        return user == null ? null : user.id();
    }

    private static String initial(String username) {
        String normalized = value(username, "?").trim();
        return normalized.isBlank() ? "?" : normalized.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String first(String... values) {
        for (String candidate : values) {
            if (candidate != null && !candidate.isBlank() && !"null".equalsIgnoreCase(candidate)) {
                return candidate;
            }
        }
        return "";
    }

    private record CommentIdentity(String userId, String username, String avatarUrl, String date) {
    }

    private record RoleBadge(String label, String color) {
    }
}
