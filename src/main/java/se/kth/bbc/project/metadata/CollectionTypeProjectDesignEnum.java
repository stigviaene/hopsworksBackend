package se.kth.bbc.project.metadata;

/**
 *
 * @author stig
 */
public enum CollectionTypeProjectDesignEnum {

  CASE_CONTROL(
          "A case-control project design compares two groups of subjects: those with the disease or condition under project (cases) and a very similar group of subjects who do not have the disease or condition (controls)."),
  COHORT("A form of longitudinal project for the analysis of risk factors following a group of people who do not have a disease, and uses correlations to determine the absolute risk of subject contraction."),
  CROSS_SECTIONAL(
          "A type of observational project that involves data collection from a population, or a representative subset, at one specific point in time."),
  LONGITUDINAL(
          "Research studies involving repeated observations of the same entity over time. In the biobank context, longitudinal studies sample a group of people in a given time period, and project them at intervals by the acquisition and analyses of data and/or samples over time."),
  TWIN_PROJECT(
          "Twin studies measure the contribution of genetics (as opposed to environment) to a given trait or condition of interest."),
  QUALITY_CONTROL(
          "A quality control testing project design type is where some aspect of the experiment is quality controlled for the purposes of quality assurance."),
  POPULATION_BASED(
          "Project done at the population level or among the population groups, generally to find the cause, incidence or spread of the disease or to see the response to the treatment, nutrition or environment."),
  DISEASE_SPECIFIC(
          "A project or biobank for which material and information is collected from subjects that have already developed a particular disease."),
  BIRTH_COHORT(
          "A corhort project for which the subjects are followed from the time of birth usually including information about gestation and follow up."),
  OTHER("");

  private final String definition;

  private CollectionTypeProjectDesignEnum(String definition) {
    this.definition = definition;
  }

  public String definition() {
    return definition;
  }

  @Override
  public String toString() {
    switch (this) {
      case CASE_CONTROL:
        return "Case-control";
      case COHORT:
        return "Cohort";
      case CROSS_SECTIONAL:
        return "Cross-sectional";
      case LONGITUDINAL:
        return "Longitudinal";
      case TWIN_PROJECT:
        return "Twin-project";
      case QUALITY_CONTROL:
        return "Quality control";
      case POPULATION_BASED:
        return "Population-based";
      case DISEASE_SPECIFIC:
        return "Disease specific";
      case BIRTH_COHORT:
        return "Birth cohort";
      case OTHER:
        return "Other";
      default:
        throw new IllegalStateException("Illegal enum value.");
    }
  }

}
