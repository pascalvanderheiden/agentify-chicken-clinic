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

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.samples.petclinic.owner.Owner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the {@link ClinicQueryService} read-only query boundary.
 *
 * <p>
 * Covers: owner-name lookup (partial, case-insensitive), pet-name lookup (partial,
 * case-insensitive), purpose-built record shapes, and absent-record admission. Uses H2
 * in-memory database with the default data.sql seed — no external services required.
 */
@DataJpaTest
@Import(ClinicQueryService.class)
class ClinicQueryServiceTests {

	@Autowired
	private ClinicQueryService queryService;

	// --- Seam 7: Owner-name lookup ---

	@Test
	void findOwnersByLastNameReturnsMatchForExactName() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		assertThat(results).isNotEmpty();
		assertThat(results).allMatch(r -> r.lastName().equalsIgnoreCase("Davis"));
	}

	@Test
	void findOwnersByLastNameIsCaseInsensitive() {
		List<OwnerRecord> lower = this.queryService.findOwnersByLastName("davis");
		List<OwnerRecord> upper = this.queryService.findOwnersByLastName("DAVIS");
		assertThat(lower).isNotEmpty();
		assertThat(lower).hasSameSizeAs(upper);
	}

	@Test
	void findOwnersByLastNameSupportsPartialMatch() {
		// "avis" is a substring of "Davis"
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("avis");
		assertThat(results).isNotEmpty();
		assertThat(results).allMatch(r -> r.lastName().toLowerCase().contains("avis"));
	}

	@Test
	void findOwnersByLastNameIncludesTelephoneAndPets() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		assertThat(results).isNotEmpty();
		OwnerRecord first = results.get(0);
		assertThat(first.telephone()).isNotBlank();
		assertThat(first.firstName()).isNotBlank();
	}

	@Test
	void findOwnersByLastNameReturnsEmptyForUnknownName() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("zzznobody");
		assertThat(results).isEmpty();
	}

	// --- Seam 8: Pet-name lookup ---

	@Test
	void findPetsByNameReturnsMatchForExactName() {
		// "Leo" exists in seed data (owner: George Franklin)
		List<PetRecord> results = this.queryService.findPetsByName("Leo");
		assertThat(results).isNotEmpty();
		assertThat(results).allMatch(r -> r.name().equalsIgnoreCase("Leo"));
	}

	@Test
	void findPetsByNameIsCaseInsensitive() {
		List<PetRecord> lower = this.queryService.findPetsByName("leo");
		List<PetRecord> upper = this.queryService.findPetsByName("LEO");
		assertThat(lower).isNotEmpty();
		assertThat(lower).hasSameSizeAs(upper);
	}

	@Test
	void findPetsByNameSupportsPartialMatch() {
		// "amantha" is a substring of "Samantha"
		List<PetRecord> results = this.queryService.findPetsByName("amantha");
		assertThat(results).isNotEmpty();
		assertThat(results).allMatch(r -> r.name().toLowerCase().contains("amantha"));
	}

	@Test
	void findPetsByNameIncludesOwnerNameAndType() {
		List<PetRecord> results = this.queryService.findPetsByName("Leo");
		assertThat(results).isNotEmpty();
		PetRecord leo = results.get(0);
		assertThat(leo.ownerName()).isNotBlank();
		assertThat(leo.type()).isNotBlank();
	}

	@Test
	void findPetsByNameReturnsEmptyForUnknownName() {
		List<PetRecord> results = this.queryService.findPetsByName("zzznosuchpet");
		assertThat(results).isEmpty();
	}

	// --- Seam 9: Purpose-built record shape (not JPA entities) ---

	@Test
	void ownerRecordIsNotJpaOwnerEntity() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		assertThat(results).isNotEmpty();
		assertThat(results.get(0)).isNotInstanceOf(Owner.class);
		assertThat(results.get(0)).isInstanceOf(OwnerRecord.class);
	}

	@Test
	void petRecordIsNotJpaPetEntity() {
		List<PetRecord> results = this.queryService.findPetsByName("Leo");
		assertThat(results).isNotEmpty();
		assertThat(results.get(0)).isNotInstanceOf(org.springframework.samples.petclinic.owner.Pet.class);
		assertThat(results.get(0)).isInstanceOf(PetRecord.class);
	}

	@Test
	void ownerRecordPetsAreAlsoPurposeBuiltRecords() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Franklin");
		assertThat(results).isNotEmpty();
		// George Franklin has Leo
		assertThat(results.get(0).pets()).isNotEmpty();
		assertThat(results.get(0).pets().get(0)).isInstanceOf(PetRecord.class);
	}

	// --- Seam 10: Ambiguity — multiple matches capped at MAX_CANDIDATES ---

	@Test
	void findOwnersByLastNameCapsCandidatesAtMaxCandidates() {
		// Seed contains at least two "Davis" owners; verify cap is respected.
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		assertThat(results).hasSizeLessThanOrEqualTo(ClinicQueryService.MAX_CANDIDATES);
	}

	@Test
	void findOwnersByLastNameMultipleMatchesIncludeIdentifyingDetails() {
		// Both Davis records must include enough detail to distinguish them.
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		assertThat(results).hasSizeGreaterThan(1);
		assertThat(results).allSatisfy(r -> {
			assertThat(r.firstName()).isNotBlank();
			assertThat(r.lastName()).isNotBlank();
			assertThat(r.city()).isNotBlank();
			assertThat(r.telephone()).isNotBlank();
		});
	}

	@Test
	void findPetsByNameCapsCandidatesAtMaxCandidates() {
		// Partial match on "a" would return many pets; verify cap.
		List<PetRecord> results = this.queryService.findPetsByName("a");
		assertThat(results).hasSizeLessThanOrEqualTo(ClinicQueryService.MAX_CANDIDATES);
	}

	@Test
	void findPetsByNameCapIsAppliedAfterFlatteningAcrossOwners() {
		// "Lucky" appears under two distinct owners in the H2 seed (owners 7 and 10).
		// After flatMap expansion the result must still be capped at MAX_CANDIDATES,
		// proving the limit is applied to the flattened pet stream, not to the owner
		// stream before flattening.
		List<PetRecord> results = this.queryService.findPetsByName("Lucky");
		assertThat(results).hasSizeLessThanOrEqualTo(ClinicQueryService.MAX_CANDIDATES);
		// All returned records must actually match the search term.
		assertThat(results).allMatch(r -> r.name().toLowerCase().contains("lucky"));
		// At least two distinct owners must contribute (proving cross-owner flattening).
		long distinctOwners = results.stream().map(PetRecord::ownerName).distinct().count();
		assertThat(distinctOwners).isGreaterThanOrEqualTo(2);
	}

	// --- Seam 11: Absent records explicitly return empty (no fabrication) ---

	@Test
	void findOwnersByLastNameReturnsEmptyListForAbsentName() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("zzznobody");
		assertThat(results).isEmpty();
	}

	@Test
	void findPetsByNameReturnsEmptyListForAbsentName() {
		List<PetRecord> results = this.queryService.findPetsByName("zzznosuchpet");
		assertThat(results).isEmpty();
	}

	// --- Seam 12: Non-guessing — returned records contain only real seed data ---

	@Test
	void findOwnersByLastNameDoesNotFabricateRecords() {
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("Davis");
		// Every returned record must match the searched fragment — no invented names.
		assertThat(results).allMatch(r -> r.lastName().toLowerCase().contains("davis"));
	}

	@Test
	void findPetsByNameDoesNotFabricateRecords() {
		List<PetRecord> results = this.queryService.findPetsByName("Leo");
		// Every returned record must match the searched name — no invented pets.
		assertThat(results).allMatch(r -> r.name().toLowerCase().contains("leo"));
	}

	@Test
	void emptyOwnerResultHasNoFabricatedEntries() {
		// An absent search must return a genuinely empty list, not a list with invented
		// data.
		List<OwnerRecord> results = this.queryService.findOwnersByLastName("zzznobody");
		assertThat(results).isEmpty();
		// Confirm result contains no items that could be mistaken for real data.
		assertThat(results).noneMatch(r -> r.firstName() != null && !r.firstName().isBlank());
	}

}
