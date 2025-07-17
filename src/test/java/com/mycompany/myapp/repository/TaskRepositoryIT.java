package com.mycompany.myapp.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myapp.IntegrationTest;
import com.mycompany.myapp.domain.Task;
import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.domain.enumeration.TaskPriority;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

@IntegrationTest
@Transactional
class TaskRepositoryIT {

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private Task task;
    private User user;

    @BeforeEach
    void setUp() {
        user = createUser("testuser");
        userRepository.saveAndFlush(user);

        task = new Task()
            .description("Test Task")
            .dueDate(LocalDate.now().plusDays(1))
            .priority(TaskPriority.HIGH)
            .completed(false)
            .createdDate(Instant.now())
            .user(user);
    }

    @Test
    void testFindAllWithEagerRelationships() {
        // Create multiple tasks with relationships
        Task task1 = createTaskWithUser("Task 1", user);
        Task task2 = createTaskWithUser("Task 2", user);
        taskRepository.saveAllAndFlush(List.of(task1, task2));

        // Test eager loading
        List<Task> tasks = taskRepository.findAllWithEagerRelationships();
        assertThat(tasks).hasSize(2);
        assertThat(tasks).allSatisfy(t -> {
            assertThat(t.getUser()).isNotNull();
            assertThat(t.getUser().getLogin()).isNotEmpty();
        });
    }

    @Test
    @WithMockUser("testuser")
    void testFindByUserIsCurrentUserWithNoTasks() {
        // Test empty result
        List<Task> tasks = taskRepository.findByUserIsCurrentUser();
        assertThat(tasks).isEmpty();
    }

    @Test
    @WithMockUser("testuser")
    void testFindByUserIsCurrentUserWithTasks() {
        // Create tasks for current user
        taskRepository.saveAndFlush(task);

        // Test finding tasks
        List<Task> tasks = taskRepository.findByUserIsCurrentUser();
        assertThat(tasks).hasSize(1);
        assertThat(tasks.get(0).getUser().getLogin()).isEqualTo("testuser");
    }

    @Test
    void testFindOneWithEagerRelationships() {
        // Save task
        Task savedTask = taskRepository.saveAndFlush(task);

        // Test finding with relationships
        assertThat(taskRepository.findOneWithEagerRelationships(savedTask.getId()))
            .isPresent()
            .hasValueSatisfying(t -> {
                assertThat(t.getUser()).isNotNull();
                assertThat(t.getUser().getLogin()).isEqualTo("testuser");
            });
    }

    private User createUser(String login) {
        User user = new User();
        user.setLogin(login);
        user.setPassword(RandomStringUtils.randomAlphanumeric(60));
        user.setActivated(true);
        user.setEmail(login + "@example.com");
        user.setFirstName("test");
        user.setLastName("user");
        user.setLangKey("en");
        return user;
    }

    private Task createTaskWithUser(String description, User user) {
        return new Task()
            .description(description)
            .dueDate(LocalDate.now().plusDays(1))
            .priority(TaskPriority.HIGH)
            .completed(false)
            .createdDate(Instant.now())
            .user(user);
    }
}
