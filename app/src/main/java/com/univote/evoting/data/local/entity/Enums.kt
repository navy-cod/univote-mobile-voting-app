package com.univote.evoting.data.local.entity

enum class Role { VOTER, ELECTION_ADMIN, SYSTEM_ADMIN, OBSERVER, SUPPORT_OFFICER }

enum class ElectionStatus { DRAFT, SCHEDULED, OPEN, SUSPENDED, CLOSED }

enum class CandidateStatus { PENDING, APPROVED, REJECTED }

enum class TicketType { TECHNICAL, FRAUD }

enum class TicketStatus { OPEN, IN_REVIEW, RESOLVED, CLOSED }

enum class NotificationType { INFO, VOTE_CONFIRMATION, SCHEDULE, RESULTS, SUPPORT }
