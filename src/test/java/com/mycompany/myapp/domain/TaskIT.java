package com.mycompany.myapp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myapp.IntegrationTest;
import com.mycompany.myapp.domain.enumeration.TaskPriority;
import com.mycompany.myapp.repository.TaskRepository;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.security.SecurityUtils;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link Task} entity.
 */
@IntegrationTest
class TaskIT {

    private static final String DEFAULT_DESCRIPTION = "Test Task Description";
    private static final String UPDATED_DESCRIPTION = "Updated Task Description";
    private static final LocalDate DEFAULT_DUE_DATE = LocalDate.now().plusDays(7);
    private static final LocalDate UPDATED_DUE_DATE = LocalDate.now().plusDays(14);
    private static final TaskPriority DEFAULT_PRIORITY = TaskPriority.LOW;
    private static final TaskPriority UPDATED_PRIORITY = TaskPriority.HIGH;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager em;

    private Task task;
    private User user;

    @BeforeEach
    public void initTest() {
        user = userRepository.findOneByLogin("admin").orElseThrow(() -> new NoSuchElementException("Admin user not found"));
        task = new Task()
            .description(DEFAULT_DESCRIPTION)
            .dueDate(DEFAULT_DUE_DATE)
            .priority(DEFAULT_PRIORITY)
            .completed(false)
            .createdDate(Instant.now())
            .user(user);
    }

    @Test
    @Transactional
    void createTask() {
        int databaseSizeBeforeCreate = taskRepository.findAll().size();

        // Create the Task
        taskRepository.save(task);

        // Validate the Task in the database
        assertThat(taskRepository.findAll()).hasSize(databaseSizeBeforeCreate + 1);
        Task testTask = taskRepository.findById(task.getId()).orElseThrow(() -> new NoSuchElementException("Task not found"));
        assertThat(testTask.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(testTask.getDueDate()).isEqualTo(DEFAULT_DUE_DATE);
        assertThat(testTask.getPriority()).isEqualTo(DEFAULT_PRIORITY);
        assertThat(testTask.getCompleted()).isFalse();
        assertThat(testTask.getCreatedDate()).isNotNull();
        assertThat(testTask.getLastModifiedDate()).isNull();
        assertThat(testTask.getUser()).isEqualTo(user);
    }

    @Test
    @Transactional
    void updateTask() {
        // Initialize the database
        taskRepository.save(task);

        int databaseSizeBeforeUpdate = taskRepository.findAll().size();

        // Update the task
        Task updatedTask = taskRepository.findById(task.getId()).orElseThrow(() -> new NoSuchElementException("Task not found"));
        updatedTask.description(UPDATED_DESCRIPTION).dueDate(UPDATED_DUE_DATE).priority(UPDATED_PRIORITY).completed(true);

        taskRepository.save(updatedTask);

        // Validate the Task in the database
        assertThat(taskRepository.findAll()).hasSize(databaseSizeBeforeUpdate);
        Task testTask = taskRepository.findById(updatedTask.getId()).orElseThrow(() -> new NoSuchElementException("Task not found"));
        assertThat(testTask.getDescription()).isEqualTo(UPDATED_DESCRIPTION);
        assertThat(testTask.getDueDate()).isEqualTo(UPDATED_DUE_DATE);
        assertThat(testTask.getPriority()).isEqualTo(UPDATED_PRIORITY);
        assertThat(testTask.getCompleted()).isTrue();
        assertThat(testTask.getLastModifiedDate()).isNotNull();
    }

    @Test
    @Transactional
    @WithMockUser(username = "admin")
    void findTasksByUser() {
        // Initialize the database
        taskRepository.save(task);

        // Get tasks for current user
        List<Task> userTasks = taskRepository.findByUserIsCurrentUser();
        assertThat(userTasks).isNotEmpty();
        assertThat(userTasks.get(0).getUser().getLogin()).isEqualTo("admin");
    }

    @Test
    @Transactional
    void findTaskWithEagerRelationships() {
        // Initialize the database
        taskRepository.save(task);

        // Get task with eager relationships
        Task foundTask = taskRepository
            .findOneWithEagerRelationships(task.getId())
            .orElseThrow(() -> new NoSuchElementException("Task not found"));
        assertThat(foundTask.getUser()).isNotNull();
        assertThat(foundTask.getUser().getLogin()).isEqualTo("admin");
    }

    @Test
    @Transactional
    void findAllWithPagination() {
        // Initialize the database
        taskRepository.save(task);

        // Get all tasks with pagination
        Page<Task> taskPage = taskRepository.findAllWithEagerRelationships(PageRequest.of(0, 20));
        assertThat(taskPage.getContent()).isNotEmpty();
        assertThat(taskPage.getContent().get(0).getUser()).isNotNull();
    }

    @Test
    @Transactional
    void checkLifecycleHooks() {
        // Create task without setting createdDate (let @PrePersist handle it)
        Task newTask = new Task()
            .description(DEFAULT_DESCRIPTION)
            .dueDate(DEFAULT_DUE_DATE)
            .priority(DEFAULT_PRIORITY)
            .completed(false)
            .user(user);

        // Save the task
        Task savedTask = taskRepository.save(newTask);
        taskRepository.flush();

        // Verify @PrePersist hooks
        assertThat(savedTask.getCreatedDate()).isNotNull();
        assertThat(savedTask.getCompleted()).isFalse();

        // Update task
        Instant beforeUpdate = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        savedTask.setDescription(UPDATED_DESCRIPTION);
        Task updatedTask = taskRepository.save(savedTask);
        taskRepository.flush();

        // Verify @PreUpdate hooks
        assertThat(updatedTask.getLastModifiedDate()).isNotNull().isAfterOrEqualTo(beforeUpdate);
    }
}
