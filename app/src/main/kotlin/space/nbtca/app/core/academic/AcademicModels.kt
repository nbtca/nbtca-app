package space.nbtca.app.core.academic

import kotlinx.serialization.Serializable

@Serializable
data class AcademicTerm(
    val academicYear: String = "",
    val semester: String = "",
    val yearLabel: String = "",
    val semesterLabel: String = ""
)

@Serializable
data class TimetableCourse(
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val weekday: Int = 0,
    val startPeriod: Int = 0,
    val endPeriod: Int = 0,
    val weeks: List<Int> = emptyList()
) {
    val periodText: String
        get() = if (startPeriod == endPeriod) "第 ${startPeriod} 节" else "第 $startPeriod-$endPeriod 节"
}

@Serializable
data class ExamItem(
    val courseName: String = "",
    val examName: String = "",
    val timeText: String = "",
    val location: String = "",
    val seat: String = "",
    val assessment: String = ""
)

@Serializable
data class GradeItem(
    val courseCode: String = "",
    val courseName: String = "",
    val grade: String = "",
    val credits: String = "",
    val gradePoint: String = "",
    val nature: String = "",
    val remark: String = "",
    val voided: Boolean = false
)

@Serializable
data class AcademicSnapshot(
    val studentId: String = "",
    val termLabel: String = "",
    val timetable: List<TimetableCourse> = emptyList(),
    val exams: List<ExamItem> = emptyList(),
    val grades: List<GradeItem> = emptyList(),
    val updatedAt: Long = 0L
)
