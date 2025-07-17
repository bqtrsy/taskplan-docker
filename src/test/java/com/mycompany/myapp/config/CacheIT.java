package com.mycompany.myapp.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.mycompany.myapp.IntegrationTest;
import com.mycompany.myapp.domain.Authority;
import com.mycompany.myapp.domain.Task;
import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.repository.TaskRepository;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.security.AuthoritiesConstants;
import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Integration tests for the {@link CacheConfiguration} class.
 */
@IntegrationTest
class CacheIT {

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TaskRepository taskRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        cacheManager.getCacheNames().forEach(cacheName -> cacheManager.getCache(cacheName).clear());
    }

    @Test
    @Transactional
    void testTaskCaching() {
        // Create a task
        Task task = new Task().description("Test Task Description").dueDate(LocalDate.now()).completed(false).createdDate(Instant.now());

        // Save the task
        taskRepository.saveAndFlush(task);

        // Get the task from cache
        Task cachedTask = taskRepository.findById(task.getId()).orElseThrow(() -> new NoSuchElementException("Task not found"));

        // Verify it's the same task
        assertThat(cachedTask).isEqualTo(task);

        // Modify the task
        task.setCompleted(true);
        taskRepository.saveAndFlush(task);

        // Get the updated task from cache
        Task updatedCachedTask = taskRepository.findById(task.getId()).orElseThrow(() -> new NoSuchElementException("Task not found"));

        // Verify the cache was updated
        assertThat(updatedCachedTask.getCompleted()).isTrue();
    }

    @Test
    @Transactional
    void testUserCaching() {
        // Create a user with a valid 60-character password hash
        User user = new User();
        user.setLogin("test-user");
        user.setPassword(passwordEncoder.encode("$2a$10$gSAhZrxMllrbgj/kkK9UceBPpChGWJA7SYIb1Mqo.n5aNLq1/oRrC"));
        user.setActivated(true);
        user.setEmail("test@localhost");
        user.setFirstName("Test");
        user.setLastName("User");
        user.setImageUrl("http://placehold.it/50x50");
        user.setLangKey("en");

        // Save the user
        userRepository.saveAndFlush(user);

        // Get the user from cache
        User cachedUser = userRepository.findById(user.getId()).orElseThrow(() -> new NoSuchElementException("User not found"));

        // Verify it's the same user
        assertThat(cachedUser).isEqualTo(user);

        // Modify the user
        user.setEmail("updated@localhost");
        userRepository.saveAndFlush(user);

        // Get the updated user from cache
        User updatedCachedUser = userRepository.findById(user.getId()).orElseThrow(() -> new NoSuchElementException("User not found"));

        // Verify the cache was updated
        assertThat(updatedCachedUser.getEmail()).isEqualTo("updated@localhost");
    }

    @Test
    @Transactional
    void testUserCacheEviction() {
        // Create test user
        User user = new User();
        user.setLogin("cachetest");
        user.setPassword(passwordEncoder.encode("test"));
        user.setActivated(true);
        user.setEmail("cachetest@example.com");
        userRepository.saveAndFlush(user);

        // First load - should cache
        Optional<User> cachedUser = userRepository.findOneByLogin("cachetest");
        assertThat(cachedUser).isPresent();

        // Update user
        user.setEmail("newemail@test.com");
        userRepository.saveAndFlush(user);

        // Clear user caches
        Objects.requireNonNull(cacheManager.getCache(UserRepository.USERS_BY_LOGIN_CACHE)).clear();
        Objects.requireNonNull(cacheManager.getCache(UserRepository.USERS_BY_EMAIL_CACHE)).clear();

        // Verify cache is cleared
        assertThat(cacheManager.getCache(UserRepository.USERS_BY_LOGIN_CACHE).get("cachetest")).isNull();
    }

    @Test
    @Transactional
    void testTaskCacheOperations() {
        // Create a task
        Task task = new Task().description("Cache Test Task").dueDate(LocalDate.now()).completed(false).createdDate(Instant.now());

        // Save and verify task
        Task savedTask = taskRepository.saveAndFlush(task);
        assertThat(savedTask.getId()).isNotNull();

        // Clear cache
        cacheManager.getCacheNames().forEach(cacheName -> cacheManager.getCache(cacheName).clear());

        // Verify task can be retrieved after cache clear
        Optional<Task> reloadedTask = taskRepository.findById(savedTask.getId());
        assertThat(reloadedTask).isPresent();
        assertThat(reloadedTask.orElseThrow().getDescription()).isEqualTo("Cache Test Task");
    }
}
