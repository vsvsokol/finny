"""Контракт сохранения соответствует Entities.kt и StoredGames.FORMAT_VERSION=15."""
from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field, model_validator

SafeId = Annotated[int, Field(ge=1, le=9_007_199_254_740_991)]
LocalId = Annotated[int, Field(ge=-9_007_199_254_740_991, le=9_007_199_254_740_991)]
Count = Annotated[int, Field(ge=0, le=2_147_483_647)]
Integer = Annotated[int, Field(ge=-2_147_483_648, le=2_147_483_647)]
Timestamp = Annotated[int, Field(ge=0, le=9_007_199_254_740_991)]
Ref = Annotated[str, Field(min_length=1, max_length=128)]


class Model(BaseModel):
    model_config = ConfigDict(extra="forbid", strict=True)


class Credentials(Model):
    login: str = Field(min_length=3, max_length=32, pattern=r"^[a-zA-Z0-9_.-]+$")
    password: str = Field(min_length=10, max_length=128)


class PasswordChange(Model):
    currentPassword: str = Field(min_length=1, max_length=128)
    newPassword: str = Field(min_length=10, max_length=128)


class Recovery(Model):
    login: str = Field(min_length=3, max_length=32, pattern=r"^[a-zA-Z0-9_.-]+$")
    recoveryCode: str = Field(min_length=1, max_length=128)
    newPassword: str = Field(min_length=10, max_length=128)


class ConfirmPassword(Model):
    password: str = Field(min_length=1, max_length=128)


class Profile(Model):
    id: LocalId
    petName: str = Field(min_length=1, max_length=64)
    petCharacter: Literal["PUSHISTIK", "ROGATIK", "ZVEZDOCHKA", "BANTIK", "LUCHIK"]
    bodyColor: Literal["A", "B", "C"]
    eyes: Literal["ROUND", "OVAL", "SLY"]
    activeGoalId: Ref | None = None
    isDemo: Literal[False] = False
    createdAt: Timestamp
    wornItemId: Ref | None = None
    wornEyesId: Ref | None = None
    roomWall: Ref | None = None
    roomWindow: Ref | None = None
    roomLamp: Ref | None = None
    roomRug: Ref | None = None
    wornLook: Ref | None = None


class Pet(Model):
    profileId: LocalId
    satiety: int = Field(ge=0, le=100)
    hygiene: int = Field(ge=0, le=100)
    mood: int = Field(ge=0, le=100)
    energy: int = Field(ge=0, le=100)
    sleepingSince: Timestamp | None = None
    playMood: Count = 0
    playSince: Timestamp | None = None


class Period(Model):
    profileId: LocalId
    number: Count
    stage: Count
    phase: Literal["PLANNING", "ACTIVE", "CLOSED"]
    budget: Count | None = None
    plannedNeeds: Count | None = None
    plannedWants: Count | None = None
    plannedSavings: Count | None = None
    factNeeds: Count | None = None
    factWants: Count | None = None
    factSavings: Count | None = None
    unplannedIncome: Count | None = None
    needsCovered: bool | None = None
    planMatched: bool | None = None
    savingsAdded: bool | None = None
    successfulTasks: Count | None = None
    pointsEarned: Count | None = None
    levelTaskId: Ref | None = None
    gamePassed: bool | None = None
    passed: bool | None = None


class Ledger(Model):
    id: SafeId
    profileId: LocalId
    periodNumber: Count
    type: Literal["INCOME", "TASK_REWARD", "PARENT_BONUS", "PURCHASE", "SAVINGS_DEPOSIT", "SAVINGS_WITHDRAW", "GOAL_COMPLETE"]
    balanceDelta: Integer
    savingsDelta: Integer
    category: Literal["needs", "wants"] | None = None
    itemId: Ref | None = None
    goalId: Ref | None = None
    taskId: Ref | None = None
    unplanned: bool
    createdAt: Timestamp


class Attempt(Model):
    id: SafeId
    profileId: LocalId
    periodNumber: Count
    taskId: Ref
    outcome: Literal["SUCCESS", "FAIL"]
    reward: Count
    createdAt: Timestamp
    seed: int | None = Field(default=None, ge=-9_223_372_036_854_775_808, le=9_223_372_036_854_775_807)
    bonus: bool = False


class Use(Model):
    id: SafeId
    profileId: LocalId
    periodNumber: Count
    itemId: Ref
    createdAt: Timestamp
    portions: Count | None = 1


class Snapshot(Model):
    profile: Profile
    pet: Pet | None = None
    periods: list[Period] = Field(default_factory=list, max_length=10_000)
    ledger: list[Ledger] = Field(default_factory=list, max_length=50_000)
    attempts: list[Attempt] = Field(default_factory=list, max_length=50_000)
    uses: list[Use] = Field(default_factory=list, max_length=50_000)

    @model_validator(mode="after")
    def relations(self):
        rows = [*self.periods, *self.ledger, *self.attempts, *self.uses]
        if self.pet:
            rows.append(self.pet)
        if any(row.profileId != self.profile.id for row in rows):
            raise ValueError("Профиль и дочерние записи должны иметь один id")
        for group in (self.ledger, self.attempts, self.uses):
            ids = [row.id for row in group]
            if ids != sorted(set(ids)):
                raise ValueError("Журнал должен содержать уникальные id по возрастанию")
        numbers = [row.number for row in self.periods]
        if len(set(numbers)) != len(numbers):
            raise ValueError("Номера периодов должны быть уникальны")
        return self

    def with_id(self, profile_id: int) -> dict:
        data = self.model_dump()
        data["profile"]["id"] = profile_id
        for group in (data["periods"], data["ledger"], data["attempts"], data["uses"]):
            for row in group:
                row["profileId"] = profile_id
        if data["pet"]:
            data["pet"]["profileId"] = profile_id
        return data


class CreateProfile(Model):
    version: Literal[15]
    snapshot: Snapshot


class SaveProfile(CreateProfile):
    expectedRevision: SafeId
    mode: Literal["append", "replace"] = "append"


class DeleteProfile(Model):
    expectedRevision: SafeId
