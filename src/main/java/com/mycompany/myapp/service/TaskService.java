package com.mycompany.myapp.service;

import com.mycompany.myapp.domain.Task;
import com.mycompany.myapp.domain.User;
import com.mycompany.myapp.repository.TaskRepository;
import com.mycompany.myapp.repository.UserRepository;
import com.mycompany.myapp.security.SecurityUtils;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.mycompany.myapp.domain.Task}.
 */
@Service
@Transactional
public class TaskService {

    private static final Logger LOG = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository, UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
    }

    /**
     * Save a task.
     *
     * @param task the entity to save.
     * @return the persisted entity.
     */
    public Task save(Task task) {
        LOG.debug("Request to save Task : {}", task);

        // Set the current user if not already set
        if (task.getUser() == null) {
            SecurityUtils.getCurrentUserLogin().flatMap(userRepository::findOneByLogin).ifPresent(task::setUser);
        }

        return taskRepository.save(task);
    }

    /**
     * Update a task.
     *
     * @param task the entity to save.
     * @return the persisted entity.
     */
    public Task update(Task task) {
        LOG.debug("Request to update Task : {}", task);
        task.setLastModifiedDate(Instant.now());
        return taskRepository.save(task);
    }

    /**
     * Partially update a task.
     *
     * @param task the entity to update partially.
     * @return the persisted entity.
     */
    public Optional<Task> partialUpdate(Task task) {
        LOG.debug("Request to partially update Task : {}", task);

        return taskRepository
            .findById(task.getId())
            .map(existingTask -> {
                if (task.getDescription() != null) {
                    existingTask.setDescription(task.getDescription());
                }
                if (task.getDueDate() != null) {
                    existingTask.setDueDate(task.getDueDate());
                }
                if (task.getPriority() != null) {
                    existingTask.setPriority(task.getPriority());
                }
                if (task.getCompleted() != null) {
                    existingTask.setCompleted(task.getCompleted());
                }
                if (task.getCreatedDate() != null) {
                    existingTask.setCreatedDate(task.getCreatedDate());
                }
                if (task.getLastModifiedDate() != null) {
                    existingTask.setLastModifiedDate(task.getLastModifiedDate());
                }

                return existingTask;
            })
            .map(taskRepository::save);
    }

    /**
     * Get all the tasks.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<Task> findAll(Pageable pageable) {
        LOG.debug("Request to get all Tasks");
        return taskRepository.findAll(pageable);
    }

    /**
     * Get all tasks for the current user.
     *
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<Task> findAllByCurrentUser(Pageable pageable) {
        LOG.debug("Request to get all Tasks for current user");
        List<Task> userTasks = taskRepository.findByUserIsCurrentUser();

        // Apply manual pagination since we're filtering in memory
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), userTasks.size());

        if (start > userTasks.size()) {
            return new PageImpl<>(List.of(), pageable, userTasks.size());
        }

        List<Task> pageContent = userTasks.subList(start, end);
        return new PageImpl<>(pageContent, pageable, userTasks.size());
    }

    /**
     * Get all tasks for the current user filtered by completion status.
     *
     * @param completed the completion status to filter by.
     * @param pageable the pagination information.
     * @return the list of entities.
     */
    @Transactional(readOnly = true)
    public Page<Task> findAllByCurrentUserAndCompleted(Boolean completed, Pageable pageable) {
        LOG.debug("Request to get all Tasks for current user with completed status: {}", completed);
        List<Task> userTasks = taskRepository
            .findByUserIsCurrentUser()
            .stream()
            .filter(task -> task.getCompleted().equals(completed))
            .toList();

        // Apply manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), userTasks.size());

        if (start > userTasks.size()) {
            return new PageImpl<>(List.of(), pageable, userTasks.size());
        }

        List<Task> pageContent = userTasks.subList(start, end);
        return new PageImpl<>(pageContent, pageable, userTasks.size());
    }

    /**
     * Get all the tasks with eager load of many-to-many relationships.
     *
     * @return the list of entities.
     */
    public Page<Task> findAllWithEagerRelationships(Pageable pageable) {
        return taskRepository.findAllWithEagerRelationships(pageable);
    }

    /**
     * Get one task by id.
     *
     * @param id the id of the entity.
     * @return the entity.
     */
    @Transactional(readOnly = true)
    public Optional<Task> findOne(Long id) {
        LOG.debug("Request to get Task : {}", id);
        return taskRepository.findOneWithEagerRelationships(id);
    }

    /**
     * Delete the task by id.
     *
     * @param id the id of the entity.
     */
    public void delete(Long id) {
        LOG.debug("Request to delete Task : {}", id);
        taskRepository.deleteById(id);
    }
}
