package controllers

import java.util.UUID
import javax.inject.Inject

import com.google.inject.Singleton
import models.domain.Person
import models.dto.ErrorResponse._
import models.dto.UpdatePerson._
import models.dto.{CreatePerson, ErrorResponse, UpdatePerson}
import play.api.Logger
import play.api.data.validation.ValidationError
import play.api.libs.json.{JsPath, JsResult, Json}
import play.api.mvc.{Action, Controller, Result}
import services.data.{CreateResult, DeleteResult, PersonsRepository, UpdateResult}

import scala.concurrent.Future

@Singleton
class PersonController @Inject()(persons: PersonsRepository) extends Controller {
  private val log = Logger(this.getClass)

  private implicit val ec = play.api.libs.concurrent.Execution.Implicits.defaultContext

  private def validateParsedResult[T](jsResult: JsResult[T]): Either[ErrorResponse, T] =
    jsResult.fold(
      (errors: Seq[(JsPath, Seq[ValidationError])]) => {
        val map = fmtValidationResults(errors)
        Left(ErrorResponse("Validation Error", map))
      },
      (t: T) => Right(t)
    )

  private def createPerson(request: CreatePerson): Person =
    Person(UUID.randomUUID(), request.firstName, request.lastName, request.studentId, request.gender)

  private def asyncHttpBadRequestGivenErrorResponse(errorResponse: ErrorResponse): Future[Result] =
    Future.successful(BadRequest(Json toJson errorResponse))

  private def httpBadRequestGivenException(exception: Exception) =
    BadRequest(Json toJson ErrorResponse(code = exception.getMessage, errors = Map.empty))

  private def httpCreatedGivenCreateResult(createResult: CreateResult) =
    Created(Json.toJson(createResult.person))

  private def httpOkGivenUpdateResult(updateResult: UpdateResult) =
    Ok(Json.toJson(updateResult.person))

  private def httpOkGivenDeleteResult(deleteResult: DeleteResult) =
    Ok(Json.toJson(Map[String, String]("removedId" -> deleteResult.deletedId.toString)))

  private def persistPersonSendHttpResponse(request: CreatePerson): Future[Result] = {
    val person = createPerson(request)
    val dataResult = persons.create(person)
    dataResult.map(_.fold(httpBadRequestGivenException, httpCreatedGivenCreateResult))
  }

  private def personDoesNotExistHttpResponse(personId: UUID): Result =
    NotFound(Json toJson ErrorResponse("Does not Exist", Map("CouldNotFind" -> s"Person with (id: $personId) does not exist")))

  /**
    * Helper method that performs the update and forms an appropriate Future[Result]
    *
    * @param personId the id of the person to be updated
    * @param update the update request which contains the new updated data
    * @return a Play-friendly Http Response in the Future
    *
    * Type Algebra:
    * UUID -> UpdatePerson -> Future Result
    */
  private def updatePerson(personId: UUID)(update: UpdatePerson): Future[Result] = {
    val futureOptPerson = persons.read(personId)
    // Type Algebra: Future Option Person -> Future Result
    futureOptPerson.flatMap(optPerson =>
      // Remove Option wrapping safely and return a Future (needed because we use flatMap above and the DAO update
      // returns a Future as well so this needs to be sequenced
      // Type Algebra: Option Person -> Future Result
      optPerson.fold(Future.successful(personDoesNotExistHttpResponse(personId)))(
        person => {
          val updatedPerson = updateExistingPerson(update)(person)
          val futureEitherUpdateResult = persons.update(updatedPerson)
          // Type Algebra: Future Either (PersonNotFound, UpdateResult) -> Future Result
          futureEitherUpdateResult.map(
            // Remove Either wrapping safely
            // Type Algebra: Either (PersonNotFound, UpdateResult) -> Result
            _.fold(_ => personDoesNotExistHttpResponse(personId), httpOkGivenUpdateResult)
          )
        }
      )
    )
  }

  def create = Action.async(parse.json) {
    implicit request =>
      log.debug("POST /persons")
      val createPersonRequest = validateParsedResult(request.body.validate[CreatePerson])
      createPersonRequest.fold(asyncHttpBadRequestGivenErrorResponse, persistPersonSendHttpResponse)
  }

  def read(personId: UUID) = Action.async {
    log.debug(s"GET /persons/$personId")
    persons.read(personId)
      .map(optPerson =>
        optPerson
          .map(person => Ok(Json.toJson(person)))
          .getOrElse(personDoesNotExistHttpResponse(personId))
      )
  }

  def update(personId: UUID) = Action.async(parse.json) {
    implicit request =>
      log.debug(s"PUT /persons/$personId")
      val update = validateParsedResult(request.body.validate[UpdatePerson])
      update.fold(asyncHttpBadRequestGivenErrorResponse, updatePerson(personId))
  }

  def delete(personId: UUID) = Action.async {
    log.debug(s"DELETE /persons/$personId")
    persons.delete(personId)
      .map(_.fold(_ => personDoesNotExistHttpResponse(personId), httpOkGivenDeleteResult))
  }

  def readAll = Action.async {
    log.debug(s"GET /persons")
    persons.all.map(results => Ok(Json.toJson(results)))
  }
}
