package services.data

import java.util.UUID
import javax.inject.Inject

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import models.domain.Person

import scala.concurrent.Future


/**
  * Note that @Inject is required due to the fact that in order for Guice to work,
  * classes must have either one (and only one) constructor annotated with @Inject
  * or a zero-argument constructor that is not private
  * @param client a fully configured Amazon DynamoDB client
  */
class DynamoDBPersonsRepository @Inject()(client: AmazonDynamoDBClient) extends PersonsRepository {
  override def create(person: Person): Future[Either[RepositoryError, CreateResult]] = ???

  override def update(person: Person): Future[Either[RepositoryError, UpdateResult]] = ???

  override def all: Future[Either[RepositoryError, Seq[Person]]] = ???

  override def delete(personId: UUID): Future[Either[RepositoryError, DeleteResult]] = ???

  override def find(personId: UUID): Future[Either[RepositoryError, Option[Person]]] = ???
}