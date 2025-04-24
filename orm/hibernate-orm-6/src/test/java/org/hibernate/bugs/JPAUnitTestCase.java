package org.hibernate.bugs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import jakarta.persistence.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

/**
 * This test case reproduces the scenario where an exercise is updated via an API.
 * The exercise instance coming from the API is created outside a transaction (detached),
 * and it does not include its lazy-loaded student participations. The test then verifies
 * that the existing student participations remain intact after the update.
 * <p>
 * Participations are not needed for the update and are not sent in the exercise update request,
 * because they are large and not necessary for the update.
 * <p>
 * This test succeeds with Hibernate version 6.4.10.Final, but fails with version 6.6.10.Final.
 */
class JPAUnitTestCase {

	private EntityManagerFactory entityManagerFactory;

	@BeforeEach
	void init() {
		entityManagerFactory = Persistence.createEntityManagerFactory("templatePU");
	}

	@AfterEach
	void destroy() {
		entityManagerFactory.close();
	}

	@Test
	void testUpdateExerciseDoesNotRemoveStudentParticipations() {
		// Create and persist an exercise with two student participations.
		EntityManager em = entityManagerFactory.createEntityManager();
		em.getTransaction().begin();

		Exercise exercise = new Exercise();
		exercise.setName("Initial Exercise");

		StudentParticipation participation1 = new StudentParticipation();
		participation1.setName("Participation 1");
		participation1.setExercise(exercise);

		StudentParticipation participation2 = new StudentParticipation();
		participation2.setName("Participation 2");
		participation2.setExercise(exercise);

		exercise.getStudentParticipations().add(participation1);
		exercise.getStudentParticipations().add(participation2);

		em.persist(exercise);
		// Persisting participations explicitly, as cascade persist is not configured here.
		em.persist(participation1);
		em.persist(participation2);

		em.getTransaction().commit();
		em.close();

		// Simulate the API payload: a detached exercise instance is created outside any transaction.
		Exercise apiExercise = new Exercise();
		apiExercise.setId(exercise.getId());
		apiExercise.setName("Updated Exercise");
		// Note: The API payload does not include studentParticipations.

		// Start a new transaction to update the managed entity using the API payload.
		em = entityManagerFactory.createEntityManager();
		em.getTransaction().begin();

		em.merge(apiExercise);

		em.getTransaction().commit();
		em.close();

		// Verify that the student participations are still present.
		em = entityManagerFactory.createEntityManager();
		Exercise verifiedExercise = em.find(Exercise.class, exercise.getId());
		assertEquals("Updated Exercise", verifiedExercise.getName(), "Exercise name should be updated");
		assertEquals(2, verifiedExercise.getStudentParticipations().size(), "Student participations should not be removed");
		em.close();
	}

	// --- Entity definitions for the purpose of the test ---
	@Entity(name = "Exercise")
	public static class Exercise {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;

		private String name;

		@OneToMany(mappedBy = "exercise", cascade = CascadeType.REMOVE, orphanRemoval = true, fetch = FetchType.LAZY)
		private java.util.Set<StudentParticipation> studentParticipations = new HashSet<>();

		public Long getId() { return id; }
		public void setId(Long id) { this.id = id; }

		public String getName() { return name; }
		public void setName(String name) { this.name = name; }

		public java.util.Set<StudentParticipation> getStudentParticipations() { return studentParticipations; }
		public void setStudentParticipations(java.util.Set<StudentParticipation> studentParticipations) { this.studentParticipations = studentParticipations; }
	}

	@Entity(name = "StudentParticipation")
	public static class StudentParticipation {
		@Id
		@GeneratedValue(strategy = GenerationType.IDENTITY)
		private Long id;

		private String name;

		@ManyToOne
		private Exercise exercise;

		public Long getId() { return id; }
		public void setId(Long id) { this.id = id; }

		public String getName() { return name; }
		public void setName(String name) { this.name = name; }

		public Exercise getExercise() { return exercise; }
		public void setExercise(Exercise exercise) { this.exercise = exercise; }

		@Override
		public boolean equals(Object o) {
			if (this == o) return true;
			if (o == null || getClass() != o.getClass()) return false;
			StudentParticipation that = (StudentParticipation) o;
			if (id == null) return false;
			return id.equals(that.id);
		}

		@Override
		public int hashCode() {
			return id != null ? id.hashCode() : 0;
		}
	}
}
