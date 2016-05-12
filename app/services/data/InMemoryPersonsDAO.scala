package services.data

import java.util.UUID
import javax.inject.Singleton

import models.domain.{Person, PersonAlreadyExists, PersonDoesNotExist}

import scala.collection.parallel.mutable
import scala.concurrent.Future

@Singleton
class InMemoryPersonsDAO extends PersonsDAO {
  private val store = mutable.ParTrieMap.empty[UUID, Person]
  private implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  override def create(person: Person): Future[Either[PersonAlreadyExists, CreateResult]] =
    Future {
      if (store.get(person.id).isDefined) {
        Left(PersonAlreadyExists())
      }
      else {
        store += (person.id -> person)
        Right(CreateResult(person.id))
      }
    }

  override def update(person: Person): Future[Either[PersonDoesNotExist, UpdateResult]] =
    Future {
      store.get(person.id)
        .map(_ => {
          store += (person.id -> person)
          Right(UpdateResult())
        }).getOrElse(Left(PersonDoesNotExist()))
    }

  override def all: Future[Seq[Person]] = Future successful store.values.toList

  override def delete(personId: UUID): Future[Either[PersonDoesNotExist, DeleteResult]] =
    Future {
      store.get(personId).map(_ => {
        store remove personId
        Right(DeleteResult())
      }).getOrElse(Left(PersonDoesNotExist()))
    }

  override def read(personId: UUID): Future[Option[Person]] =
    Future {
      store.get(personId)
    }
}