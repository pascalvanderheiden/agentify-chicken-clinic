/*
 * Copyright 2012-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.samples.petclinic.assistant;

import java.util.List;

import org.springframework.samples.petclinic.owner.OwnerRepository;
import org.springframework.samples.petclinic.vet.VetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only query boundary for the Clinic Assistant. Maps JPA entities to purpose-built
 * records; no write operations are exposed.
 */
@Service
@Transactional(readOnly = true)
class ClinicQueryService {

	private final OwnerRepository ownerRepository;

	private final VetRepository vetRepository;

	ClinicQueryService(OwnerRepository ownerRepository, VetRepository vetRepository) {
		this.ownerRepository = ownerRepository;
		this.vetRepository = vetRepository;
	}

	/** Maximum number of candidate records returned for any single lookup. */
	static final int MAX_CANDIDATES = 5;

	/**
	 * Find owners whose last name contains the given fragment (partial,
	 * case-insensitive). Results are capped at {@link #MAX_CANDIDATES} so ambiguous
	 * responses remain readable and staff are prompted to narrow rather than scroll.
	 */
	List<OwnerRecord> findOwnersByLastName(String lastName) {
		return this.ownerRepository.findByLastNameContainingIgnoreCase(lastName)
			.stream()
			.limit(MAX_CANDIDATES)
			.map(this::toOwnerRecord)
			.toList();
	}

	/**
	 * Find pets whose name contains the given fragment (partial, case-insensitive).
	 * Results are capped at {@link #MAX_CANDIDATES}.
	 */
	List<PetRecord> findPetsByName(String petName) {
		String needle = petName.toLowerCase();
		return this.ownerRepository.findByPetNameContainingIgnoreCase(petName)
			.stream()
			.flatMap(owner -> owner.getPets()
				.stream()
				.filter(p -> p.getName() != null && p.getName().toLowerCase().contains(needle))
				.map(p -> new PetRecord(p.getId(), p.getName(), p.getType() != null ? p.getType().getName() : "unknown",
						p.getBirthDate(),
						p.getVisits().stream().map(v -> new VisitRecord(v.getDate(), v.getDescription())).toList(),
						owner.getFirstName() + " " + owner.getLastName())))
			.limit(MAX_CANDIDATES)
			.toList();
	}

	/** List all veterinarians with their specialties. */
	List<VetRecord> listVets() {
		return this.vetRepository.findAll()
			.stream()
			.map(v -> new VetRecord(v.getId(), v.getFirstName(), v.getLastName(),
					v.getSpecialties().stream().map(s -> s.getName()).toList()))
			.toList();
	}

	private OwnerRecord toOwnerRecord(org.springframework.samples.petclinic.owner.Owner owner) {
		String ownerName = owner.getFirstName() + " " + owner.getLastName();
		List<PetRecord> pets = owner.getPets()
			.stream()
			.map(p -> new PetRecord(p.getId(), p.getName(), p.getType() != null ? p.getType().getName() : "unknown",
					p.getBirthDate(),
					p.getVisits().stream().map(v -> new VisitRecord(v.getDate(), v.getDescription())).toList(),
					ownerName))
			.toList();
		return new OwnerRecord(owner.getId(), owner.getFirstName(), owner.getLastName(), owner.getAddress(),
				owner.getCity(), owner.getTelephone(), pets);
	}

}
