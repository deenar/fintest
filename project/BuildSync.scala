import sbt._
import sbt.Keys._
import sbt.Project.Initialize
import scala.xml._

object BuildSync {
  val buildSyncExclude = SettingKey[Set[String]]("build-sync-exclusions")
  val buildSync = TaskKey[Unit]("build-sync-check", "Check that both build systems are in sync")

  lazy val settings = Seq(
    buildSyncExclude := Set.empty,
    buildSync <<= buildSyncTask)

  def aggregated(projectRef: ProjectRef, structure: Load.BuildStructure, exclude: Set[String]): Seq[String] = {
    val aggregate = Project.getProject(projectRef, structure).toSeq.flatMap(_.aggregate)
    aggregate flatMap { ref =>
      if (exclude contains ref.project) Seq.empty
      else ref.project +: aggregated(ref, structure, exclude)
    }
  }

  def buildSyncTask = (thisProjectRef, buildStructure, buildSyncExclude) map {
    (projectRef, structure, exclusions) => {
      val projects = aggregated(projectRef, structure, exclusions).toSet
      val mvnConfig = XML.loadFile("pom.xml")

      val mvnProjects = (mvnConfig \ "modules" \ "module") map(_.text) toSet
      val configSubProjects = mvnProjects &~ exclusions

      val missingFromSbt = configSubProjects &~ projects
      val missingFromMvn = projects &~ configSubProjects

      if (missingFromSbt.nonEmpty || missingFromMvn.nonEmpty)
        sys error("Builds are out of sync. Missing from SBT: (%s). Missing from Maven: (%s).".format(
          missingFromSbt.mkString(", "), missingFromMvn.mkString(", ")))
    }
  }
}
