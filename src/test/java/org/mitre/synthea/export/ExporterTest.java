package org.mitre.synthea.export;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;
import org.mitre.synthea.helpers.Utilities;
import org.mitre.synthea.modules.DeathModule;
import org.mitre.synthea.world.agents.Person;
import org.mitre.synthea.world.concepts.HealthRecord;
import org.mitre.synthea.world.concepts.HealthRecord.Encounter;

public class ExporterTest {

  private long time;
  private long endTime;
  private int yearsToKeep;
  private Person patient;
  private HealthRecord record;
  
  private static final HealthRecord.Code DUMMY_CODE = new HealthRecord.Code("", "", "");
  
  /**
   * Setup test data.
   */
  @Before public void setup() {
    endTime = time = System.currentTimeMillis();
    yearsToKeep = 5;
    patient = new Person(12345L);
    record = patient.record;
    record.encounterStart(endTime, "dummy encounter");
  }

  @Test public void test_export_filter_simple_cutoff() {
    record.observation(time - years(8), "height", 64);
    record.observation(time - years(4), "weight", 128);

    // observations should be filtered to the cutoff date

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    Encounter encounter = filtered.record.currentEncounter(time);
    assertEquals(1, encounter.observations.size());
    assertEquals("weight", encounter.observations.get(0).type);
    assertEquals(time - years(4), encounter.observations.get(0).start);
    assertEquals(128, encounter.observations.get(0).value);
  }

  @Test public void test_export_filter_should_keep_old_active_medication() {
    record.medicationStart(time - years(10), "fakeitol");

    record.medicationStart(time - years(8), "placebitol");
    record.medicationEnd(time - years(6), "placebitol", DUMMY_CODE);

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    Encounter encounter = filtered.record.currentEncounter(time);
    assertEquals(1, encounter.medications.size());
    assertEquals("fakeitol", encounter.medications.get(0).type);
    assertEquals(time - years(10), encounter.medications.get(0).start);
  }

  @Test public void test_export_filter_should_keep_medication_that_ended_during_target() {
    record.medicationStart(time - years(10), "dimoxinil");
    record.medicationEnd(time - years(9), "dimoxinil", DUMMY_CODE);

    record.medicationStart(time - years(8), "placebitol");
    record.medicationEnd(time - years(4), "placebitol", DUMMY_CODE);

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    Encounter encounter = filtered.record.currentEncounter(time);
    assertEquals(1, encounter.medications.size());
    assertEquals("placebitol", encounter.medications.get(0).type);
    assertEquals(time - years(8), encounter.medications.get(0).start);
    assertEquals(time - years(4), encounter.medications.get(0).stop);
  }

  @Test public void test_export_filter_should_keep_old_active_careplan() {
    record.careplanStart(time - years(10), "stop_smoking");
    record.careplanEnd(time - years(8), "stop_smoking", DUMMY_CODE);

    record.careplanStart(time - years(12), "healthy_diet");

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    Encounter encounter = filtered.record.currentEncounter(time);
    assertEquals(1, encounter.careplans.size());
    assertEquals("healthy_diet", encounter.careplans.get(0).type);
    assertEquals(time - years(12), encounter.careplans.get(0).start);
  }

  @Test public void test_export_filter_should_keep_careplan_that_ended_during_target() {
    record.careplanStart(time - years(10), "stop_smoking");
    record.careplanEnd(time - years(1), "stop_smoking", DUMMY_CODE);

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    Encounter encounter = filtered.record.currentEncounter(time);
    assertEquals(1, encounter.careplans.size());
    assertEquals("stop_smoking", encounter.careplans.get(0).type);
    assertEquals(time - years(10), encounter.careplans.get(0).start);
    assertEquals(time - years(1), encounter.careplans.get(0).stop);
  }

  @Test public void test_export_filter_should_keep_old_active_conditions() {
    record.conditionStart(time - years(10), "fakitis");
    record.conditionEnd(time - years(8), "fakitis");

    record.conditionStart(time - years(10), "fakosis");

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    Encounter encounter = filtered.record.currentEncounter(time);
    assertEquals(1, encounter.conditions.size());
    assertEquals("fakosis", encounter.conditions.get(0).type);
    assertEquals(time - years(10), encounter.conditions.get(0).start);
  }

  @Test public void test_export_filter_should_keep_condition_that_ended_during_target() {
    record.conditionStart(time - years(10), "boneitis");
    record.conditionEnd(time - years(2), "boneitis");

    record.conditionStart(time - years(10), "smallpox");
    record.conditionEnd(time - years(9), "smallpox");

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    Encounter encounter = filtered.record.currentEncounter(time);
    assertEquals(1, encounter.conditions.size());
    assertEquals("boneitis", encounter.conditions.get(0).type);
    assertEquals(time - years(10), encounter.conditions.get(0).start);
  }

  @Test public void test_export_filter_should_keep_cause_of_death() {
    record.encounters.clear(); // delete that dummy encounter
    
    HealthRecord.Code causeOfDeath = 
        new HealthRecord.Code("SNOMED-CT", "Todo-lookup-code", "Rabies");
    patient.recordDeath(time - years(20), causeOfDeath, "death");
    
    DeathModule.process(patient, time - years(20));
    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);

    assertEquals(1, filtered.record.encounters.size());
    Encounter encounter = filtered.record.encounters.get(0);
    assertEquals(DeathModule.DEATH_CERTIFICATION, encounter.codes.get(0));
    assertEquals(time - years(20), encounter.start);

    assertEquals(1, encounter.observations.size());
    assertEquals(DeathModule.CAUSE_OF_DEATH_CODE.code, encounter.observations.get(0).type);
    assertEquals(time - years(20), encounter.observations.get(0).start);

    assertEquals(1, encounter.reports.size());
    assertEquals(DeathModule.DEATH_CERTIFICATE.code, encounter.reports.get(0).type);
    assertEquals(time - years(20), encounter.reports.get(0).start);
  }

  @Test public void test_export_filter_should_not_keep_old_stuff() {
    record.encounters.clear(); // delete that dummy encounter

    record.encounterStart(time - years(18), "er_visit");
    record.procedure(time - years(20), "appendectomy");
    record.immunization(time - years(12), "flu_shot");
    record.observation(time - years(10), "weight", 123);
    

    Person filtered = Exporter.filterForExport(patient, yearsToKeep, endTime);
    
    assertTrue(filtered.record.encounters.isEmpty());
  }
  
  private static long years(long numYears) {
    return Utilities.convertTime("years", numYears);
  }
}
