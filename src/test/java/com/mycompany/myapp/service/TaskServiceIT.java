package com.mycompany.myapp.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mycompany.myapp.IntegrationTest;
import com.mycompany.myapp.domain.Task;
import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.domain.enumeration.TaskPriority;
import com.mycompany.myapp.repository.TaskRepository;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.security.AuthoritiesConstants;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for {@link TaskService}.
 */
@IntegrationTest
@Transactional
class TaskServiceIT {

    private static final String DEFAULT_DESCRIPTION = "Test Task Description";
    private static final LocalDate DEFAULT_DUE_DATE = LocalDate.now().plusDays(1);
    private static final TaskPriority DEFAULT_PRIORITY = TaskPriority.HIGH;
    private static final Boolean DEFAULT_COMPLETED = false;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TaskService taskService;

    @Autowired
    private EntityManager em;

    private Task task;
    private User user;

    @BeforeEach
    void setUp() {
        user = createUser("testuser");
        userRepository.saveAndFlush(user);

        task = new Task()
            .description(DEFAULT_DESCRIPTION)
            .dueDate(DEFAULT_DUE_DATE)
            .priority(DEFAULT_PRIORITY)
            .completed(DEFAULT_COMPLETED)
            .createdDate(Instant.now())
            .user(user);
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void createTask() {
        long initialCount = taskRepository.count();

        // Create task
        Task savedTask = taskService.save(task);

        // Verify
        assertThat(taskRepository.count()).isEqualTo(initialCount + 1);
        assertThat(savedTask.getDescription()).isEqualTo(DEFAULT_DESCRIPTION);
        assertThat(savedTask.getDueDate()).isEqualTo(DEFAULT_DUE_DATE);
        assertThat(savedTask.getPriority()).isEqualTo(DEFAULT_PRIORITY);
        assertThat(savedTask.getCompleted()).isEqualTo(DEFAULT_COMPLETED);
        assertThat(savedTask.getUser()).isNotNull();
        assertThat(savedTask.getUser().getLogin()).isEqualTo("testuser");
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void createTaskWithoutUser() {
        task.setUser(null);
        Task savedTask = taskService.save(task);

        assertThat(savedTask.getUser()).isNotNull();
        assertThat(savedTask.getUser().getLogin()).isEqualTo("testuser");
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void findAllByCurrentUser() {
        // Create tasks for current user
        taskRepository.saveAndFlush(task);
        Task task2 = createTask("Task 2", user);
        taskRepository.saveAndFlush(task2);

        // Create task for different user
        User otherUser = createUser("otheruser");
        userRepository.saveAndFlush(otherUser);
        Task otherUserTask = createTask("Other user task", otherUser);
        taskRepository.saveAndFlush(otherUserTask);

        // Get tasks for current user
        Page<Task> userTasks = taskService.findAllByCurrentUser(PageRequest.of(0, 10));

        assertThat(userTasks.getContent()).hasSize(2);
        assertThat(userTasks.getContent()).allMatch(t -> t.getUser().getLogin().equals("testuser"));
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void findAllByCurrentUserAndCompleted() {
        // Create completed and incomplete tasks
        task.setCompleted(true);
        taskRepository.saveAndFlush(task);

        Task incompleteTask = createTask("Incomplete task", user);
        incompleteTask.setCompleted(false);
        taskRepository.saveAndFlush(incompleteTask);

        // Get completed tasks
        Page<Task> completedTasks = taskService.findAllByCurrentUserAndCompleted(true, PageRequest.of(0, 10));
        assertThat(completedTasks.getContent()).hasSize(1);
        assertThat(completedTasks.getContent().get(0).getCompleted()).isTrue();

        // Get incomplete tasks
        Page<Task> incompleteTasks = taskService.findAllByCurrentUserAndCompleted(false, PageRequest.of(0, 10));
        assertThat(incompleteTasks.getContent()).hasSize(1);
        assertThat(incompleteTasks.getContent().get(0).getCompleted()).isFalse();
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void partialUpdateTask() {
        // Create initial task
        Task savedTask = taskRepository.saveAndFlush(task);

        // Update only description and priority
        Task partialUpdate = new Task();
        partialUpdate.setId(savedTask.getId());
        partialUpdate.setDescription("Updated description");
        partialUpdate.setPriority(TaskPriority.LOW);

        Optional<Task> updatedTask = taskService.partialUpdate(partialUpdate);

        // Verify only specified fields were updated
        assertTrue(updatedTask.isPresent());
        assertEquals("Updated description", updatedTask.orElseThrow().getDescription());
        assertEquals(TaskPriority.LOW, updatedTask.orElseThrow().getPriority());
        assertEquals(DEFAULT_DUE_DATE, updatedTask.orElseThrow().getDueDate());
        assertEquals(DEFAULT_COMPLETED, updatedTask.orElseThrow().getCompleted());
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void deleteTask() {
        // Create task
        Task savedTask = taskRepository.saveAndFlush(task);
        long initialCount = taskRepository.count();

        // Delete task
        taskService.delete(savedTask.getId());

        // Verify
        assertThat(taskRepository.count()).isEqualTo(initialCount - 1);
        assertFalse(taskRepository.findById(savedTask.getId()).isPresent());
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void findAllWithEagerRelationships() {
        // Create tasks
        taskRepository.saveAndFlush(task);
        Task task2 = createTask("Task 2", user);
        taskRepository.saveAndFlush(task2);

        // Get all tasks with eager relationships
        Page<Task> tasks = taskService.findAllWithEagerRelationships(PageRequest.of(0, 10));

        assertThat(tasks.getContent()).hasSize(2);
        assertThat(tasks.getContent()).allMatch(t -> t.getUser() != null);
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void updateTask() {
        // Create initial task
        Task savedTask = taskRepository.saveAndFlush(task);

        // Update all fields
        savedTask.setDescription("Fully updated description");
        savedTask.setDueDate(DEFAULT_DUE_DATE.plusDays(1));
        savedTask.setPriority(TaskPriority.LOW);
        savedTask.setCompleted(true);

        Task updatedTask = taskService.update(savedTask);

        // Verify all fields were updated
        assertThat(updatedTask.getDescription()).isEqualTo("Fully updated description");
        assertThat(updatedTask.getDueDate()).isEqualTo(DEFAULT_DUE_DATE.plusDays(1));
        assertThat(updatedTask.getPriority()).isEqualTo(TaskPriority.LOW);
        assertThat(updatedTask.getCompleted()).isTrue();
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void findAll() {
        // Create tasks for multiple users
        taskRepository.saveAndFlush(task);
        Task task2 = createTask("Task 2", user);
        taskRepository.saveAndFlush(task2);

        User otherUser = createUser("otheruser");
        userRepository.saveAndFlush(otherUser);
        Task otherUserTask = createTask("Other user task", otherUser);
        taskRepository.saveAndFlush(otherUserTask);

        // Get all tasks (should include tasks from all users)
        Page<Task> allTasks = taskService.findAll(PageRequest.of(0, 10));

        assertThat(allTasks.getContent()).hasSize(3);
        assertThat(allTasks.getContent())
            .extracting("description")
            .containsExactlyInAnyOrder(DEFAULT_DESCRIPTION, "Task 2", "Other user task");
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void findOneWithEagerRelationships() {
        // Create and save task
        Task savedTask = taskRepository.saveAndFlush(task);

        // Find task by id
        Optional<Task> foundTask = taskService.findOne(savedTask.getId());

        // Verify
        assertThat(foundTask).isPresent();
        assertThat(foundTask.orElseThrow().getId()).isEqualTo(savedTask.getId());
        assertThat(foundTask.orElseThrow().getUser()).isNotNull();
        assertThat(foundTask.orElseThrow().getUser().getLogin()).isEqualTo("testuser");
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void paginationEdgeCases() {
        // Create multiple tasks
        List<Task> tasks = List.of(
            task,
            createTask("Task 2", user),
            createTask("Task 3", user),
            createTask("Task 4", user),
            createTask("Task 5", user)
        );
        tasks.forEach(t -> taskRepository.saveAndFlush(t));

        // Test empty page (page number too high)
        Page<Task> emptyPage = taskService.findAllByCurrentUser(PageRequest.of(10, 2));
        assertThat(emptyPage.getContent()).isEmpty();
        assertThat(emptyPage.getTotalElements()).isEqualTo(5);

        // Test last page with fewer elements
        Page<Task> lastPage = taskService.findAllByCurrentUser(PageRequest.of(2, 2));
        assertThat(lastPage.getContent()).hasSize(1);
        assertThat(lastPage.getTotalElements()).isEqualTo(5);

        // Test exact page size
        Page<Task> fullPage = taskService.findAllByCurrentUser(PageRequest.of(0, 2));
        assertThat(fullPage.getContent()).hasSize(2);
        assertThat(fullPage.getTotalElements()).isEqualTo(5);
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void testPartialUpdateWithAllFields() {
        // Create initial task
        Task savedTask = taskRepository.saveAndFlush(task);

        // Create partial update
        Task partialTask = new Task()
            .id(savedTask.getId())
            .description("Updated Description")
            .dueDate(LocalDate.now().plusDays(5))
            .priority(TaskPriority.HIGH)
            .completed(true);

        Optional<Task> result = taskService.partialUpdate(partialTask);

        assertThat(result).isPresent();
        Task updatedTask = result.orElseThrow();
        assertThat(updatedTask.getDescription()).isEqualTo("Updated Description");
        assertThat(updatedTask.getDueDate()).isEqualTo(LocalDate.now().plusDays(5));
        assertThat(updatedTask.getPriority()).isEqualTo(TaskPriority.HIGH);
        assertThat(updatedTask.getCompleted()).isTrue();
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void testFindAllByCurrentUserAndCompletedWithEmptyResult() {
        // Test pagination with no results
        Page<Task> emptyPage = taskService.findAllByCurrentUserAndCompleted(true, PageRequest.of(10, 5));

        assertThat(emptyPage.getContent()).isEmpty();
        assertThat(emptyPage.getTotalElements()).isZero();
    }

    @Test
    @Transactional
    @WithMockUser("testuser")
    void testCompleteTaskWorkflow() {
        // 1. Create task with all required fields
        Task newTask = new Task()
            .description("Initial Description")
            .dueDate(LocalDate.now().plusDays(1))
            .priority(TaskPriority.HIGH)
            .completed(false)
            .createdDate(Instant.now())
            .user(user);

        Task savedTask = taskService.save(newTask);
        assertThat(savedTask.getId()).isNotNull();
        assertThat(savedTask.getCompleted()).isFalse();

        // 2. Update task description
        savedTask.setDescription("Updated Description");
        Task updatedTask = taskService.update(savedTask);
        assertThat(updatedTask.getDescription()).isEqualTo("Updated Description");

        // 3. Complete task
        updatedTask.setCompleted(true);
        Task completedTask = taskService.update(updatedTask);

        // 4. Verify final state
        assertThat(completedTask).isNotNull();
        assertThat(completedTask.getCompleted()).isTrue();
        assertThat(completedTask.getLastModifiedDate()).isNotNull();
    }

    private User createUser(String login) {
        User newUser = new User();
        newUser.setLogin(login);
        newUser.setPassword(RandomStringUtils.randomAlphanumeric(60));
        newUser.setActivated(true);
        newUser.setEmail(login + "@localhost");
        newUser.setFirstName("testFirstName");
        newUser.setLastName("testLastName");
        newUser.setLangKey("en");
        return newUser;
    }

    private Task createTask(String description, User owner) {
        Task newTask = new Task()
            .description(description)
            .dueDate(DEFAULT_DUE_DATE)
            .priority(DEFAULT_PRIORITY)
            .completed(DEFAULT_COMPLETED)
            .createdDate(Instant.now())
            .user(owner);
        return newTask;
    }
}
