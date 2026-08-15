import { PrismaClient, UserRole, Gender, NotificationChannel } from "@prisma/client";
import bcrypt from "bcryptjs";

const prisma = new PrismaClient();

async function main() {
  const passwordHash = await bcrypt.hash("password123", 10);

  const clinic = await prisma.clinic.upsert({
    where: { id: "seed-clinic-1" },
    update: {},
    create: {
      id: "seed-clinic-1",
      name: "Sunrise Care Clinic",
      timezone: "Asia/Kolkata",
    },
  });

  const department = await prisma.department.upsert({
    where: { clinicId_name: { clinicId: clinic.id, name: "General Medicine" } },
    update: {},
    create: {
      clinicId: clinic.id,
      name: "General Medicine",
      code: "GM",
    },
  });

  const admin = await prisma.user.upsert({
    where: { email: "admin@clinic.local" },
    update: {},
    create: {
      email: "admin@clinic.local",
      passwordHash,
      role: UserRole.ADMIN,
      fullName: "Clinic Admin",
      clinicId: clinic.id,
    },
  });

  const employee = await prisma.user.upsert({
    where: { email: "employee@clinic.local" },
    update: {},
    create: {
      email: "employee@clinic.local",
      passwordHash,
      role: UserRole.EMPLOYEE,
      fullName: "Priya Desk",
      clinicId: clinic.id,
    },
  });

  const doctorUser = await prisma.user.upsert({
    where: { email: "doctor@clinic.local" },
    update: {},
    create: {
      email: "doctor@clinic.local",
      passwordHash,
      role: UserRole.DOCTOR,
      fullName: "Dr. Mehta",
      clinicId: clinic.id,
    },
  });

  const doctor = await prisma.doctor.upsert({
    where: { userId: doctorUser.id },
    update: {},
    create: {
      clinicId: clinic.id,
      userId: doctorUser.id,
      departmentId: department.id,
      specialty: "General Medicine",
      avgConsultMinutes: 15,
    },
  });

  const patientUser = await prisma.user.upsert({
    where: { email: "patient@clinic.local" },
    update: {},
    create: {
      email: "patient@clinic.local",
      phone: "9876543210",
      passwordHash,
      role: UserRole.PATIENT,
      fullName: "Anita Sharma",
      clinicId: clinic.id,
    },
  });

  const patient = await prisma.patient.upsert({
    where: { userId: patientUser.id },
    update: {},
    create: {
      clinicId: clinic.id,
      userId: patientUser.id,
      phone: "9876543210",
      fullName: "Anita Sharma",
      dateOfBirth: new Date("1988-03-12"),
      gender: Gender.FEMALE,
      email: "anita@example.com",
      smsConsent: true,
      emailConsent: true,
    },
  });

  // Family member sharing same phone
  await prisma.patient.upsert({
    where: { id: "seed-patient-child" },
    update: {},
    create: {
      id: "seed-patient-child",
      clinicId: clinic.id,
      phone: "9876543210",
      fullName: "Rohan Sharma",
      dateOfBirth: new Date("2015-07-04"),
      gender: Gender.MALE,
      smsConsent: true,
      emailConsent: false,
    },
  });

  // Mon–Fri morning + afternoon for seeded doctor
  for (const day of [1, 2, 3, 4, 5]) {
    for (const [startTime, endTime] of [
      ["09:00", "13:00"],
      ["14:00", "18:00"],
    ] as const) {
      await prisma.doctorSchedule.upsert({
        where: {
          doctorId_dayOfWeek_startTime: { doctorId: doctor.id, dayOfWeek: day, startTime },
        },
        update: { endTime, slotMinutes: 15 },
        create: {
          doctorId: doctor.id,
          dayOfWeek: day,
          startTime,
          endTime,
          slotMinutes: 15,
        },
      });
    }
  }

  const templates = [
    {
      eventCode: "BOOKING_CONFIRMED",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: Hi {{patient_name}}, your visit with {{doctor_name}} is booked for {{scheduled_time}}.",
    },
    {
      eventCode: "BOOKING_CONFIRMED",
      channel: NotificationChannel.EMAIL,
      subject: "Appointment confirmed — {{scheduled_time}}",
      bodyTemplate:
        "Dear {{patient_name}}, your appointment with {{doctor_name}} at {{clinic_name}} is confirmed for {{scheduled_time}}.",
    },
    {
      eventCode: "CHECKED_IN",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: {{patient_name}} checked in. Token {{token}} for {{doctor_name}}.",
    },
    {
      eventCode: "CHECKED_IN",
      channel: NotificationChannel.EMAIL,
      subject: "Checked in — token {{token}}",
      bodyTemplate:
        "Dear {{patient_name}}, you are checked in for {{doctor_name}}. Token {{token}}.",
    },
    {
      eventCode: "CHECKUP_STARTED",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: Your checkup with {{doctor_name}} has started. Token {{token}}.",
    },
    {
      eventCode: "DOCTOR_DELAYED",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: {{doctor_name}} is running about {{delay_minutes}} minutes late. Updated time ~{{estimated_time}}. Token {{token}}.",
    },
    {
      eventCode: "DOCTOR_DELAYED",
      channel: NotificationChannel.EMAIL,
      subject: "Update — doctor running late",
      bodyTemplate:
        "Dear {{patient_name}}, {{doctor_name}} is delayed by about {{delay_minutes}} minutes. Estimated consult {{estimated_time}}. Token {{token}}.",
    },
    {
      eventCode: "DOCTOR_DELAYED",
      channel: NotificationChannel.PUSH,
      subject: "Doctor delayed",
      bodyTemplate:
        "{{doctor_name}} is ~{{delay_minutes}} min late. New ETA {{estimated_time}}. Token {{token}}.",
    },
    {
      eventCode: "YOU_ARE_NEXT",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: You're next (token {{token}}) for {{doctor_name}}. Please be ready.",
    },
    {
      eventCode: "YOU_ARE_NEXT",
      channel: NotificationChannel.EMAIL,
      subject: "You're next",
      bodyTemplate:
        "Dear {{patient_name}}, you are next for {{doctor_name}}. Token {{token}}. Please be ready.",
    },
    {
      eventCode: "YOU_ARE_NEXT",
      channel: NotificationChannel.PUSH,
      subject: "You're next",
      bodyTemplate:
        "Token {{token}} — you're next for {{doctor_name}}. Please be ready.",
    },
    {
      eventCode: "STATUS_UPDATE",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: Timeline — token {{token}}, position {{queue_position}}, ETA {{estimated_time}} with {{doctor_name}}.",
    },
    {
      eventCode: "STATUS_UPDATE",
      channel: NotificationChannel.EMAIL,
      subject: "Your visit timeline update",
      bodyTemplate:
        "Dear {{patient_name}}, token {{token}}, queue position {{queue_position}}, estimated consult {{estimated_time}} with {{doctor_name}}.",
    },
    {
      eventCode: "STATUS_UPDATE",
      channel: NotificationChannel.PUSH,
      subject: "Visit timeline update",
      bodyTemplate:
        "Token {{token}}: position {{queue_position}}, {{patients_ahead}} ahead, ETA {{estimated_time}}.",
    },
    {
      eventCode: "VISIT_COMPLETED",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: Visit with {{doctor_name}} is complete. Total {{fee_currency}} {{total_fee}}.",
    },
    {
      eventCode: "VISIT_COMPLETED",
      channel: NotificationChannel.EMAIL,
      subject: "Visit completed",
      bodyTemplate:
        "Dear {{patient_name}}, your consultation is complete. Total fee {{fee_currency}} {{total_fee}}.",
    },
    {
      eventCode: "VISIT_COMPLETED",
      channel: NotificationChannel.PUSH,
      subject: "Visit completed",
      bodyTemplate:
        "Visit with {{doctor_name}} is complete. Total {{fee_currency}} {{total_fee}}.",
    },
    {
      eventCode: "OVERTIME_FEE",
      channel: NotificationChannel.SMS,
      bodyTemplate:
        "{{clinic_name}}: Extra charge {{overtime_fee}} applied after fixed consult time. Total {{fee_currency}} {{total_fee}}.",
    },
    {
      eventCode: "OVERTIME_FEE",
      channel: NotificationChannel.EMAIL,
      subject: "Extra consult fee applied",
      bodyTemplate:
        "Dear {{patient_name}}, consult exceeded fixed time. Extra {{overtime_fee}}; total {{fee_currency}} {{total_fee}}.",
    },
  ];

  for (const t of templates) {
    const subject = "subject" in t ? (t.subject as string) : null;
    await prisma.notificationTemplate.upsert({
      where: {
        clinicId_eventCode_channel: {
          clinicId: clinic.id,
          eventCode: t.eventCode,
          channel: t.channel,
        },
      },
      update: { bodyTemplate: t.bodyTemplate, subject },
      create: {
        clinicId: clinic.id,
        eventCode: t.eventCode,
        channel: t.channel,
        subject,
        bodyTemplate: t.bodyTemplate,
        isEnabled: true,
      },
    });
  }

  // Second hospital for public booking dropdown demos
  const clinic2 = await prisma.clinic.upsert({
    where: { id: "seed-clinic-2" },
    update: {},
    create: {
      id: "seed-clinic-2",
      name: "Apollo Daycare Hospital",
      timezone: "Asia/Kolkata",
    },
  });

  const dept2 = await prisma.department.upsert({
    where: {
      clinicId_name: { clinicId: clinic2.id, name: "General Physician" },
    },
    update: {},
    create: {
      clinicId: clinic2.id,
      name: "General Physician",
      code: "GP",
    },
  });

  await prisma.user.upsert({
    where: { email: "employee.apollo@clinic.local" },
    update: { clinicId: clinic2.id, passwordHash },
    create: {
      email: "employee.apollo@clinic.local",
      passwordHash,
      role: UserRole.EMPLOYEE,
      fullName: "Apollo Desk",
      clinicId: clinic2.id,
    },
  });

  const doctor2User = await prisma.user.upsert({
    where: { email: "doctor.apollo@clinic.local" },
    update: { clinicId: clinic2.id, passwordHash },
    create: {
      email: "doctor.apollo@clinic.local",
      passwordHash,
      role: UserRole.DOCTOR,
      fullName: "Dr. Amit Sharma",
      clinicId: clinic2.id,
    },
  });

  const doctor2 = await prisma.doctor.upsert({
    where: { userId: doctor2User.id },
    update: {},
    create: {
      clinicId: clinic2.id,
      userId: doctor2User.id,
      departmentId: dept2.id,
      specialty: "General Physician",
      avgConsultMinutes: 15,
      baseConsultFee: 500,
    },
  });

  for (const day of [1, 2, 3, 4, 5, 6]) {
    await prisma.doctorSchedule.upsert({
      where: {
        doctorId_dayOfWeek_startTime: {
          doctorId: doctor2.id,
          dayOfWeek: day,
          startTime: "19:00",
        },
      },
      update: { endTime: "22:00", slotMinutes: 15 },
      create: {
        doctorId: doctor2.id,
        dayOfWeek: day,
        startTime: "19:00",
        endTime: "22:00",
        slotMinutes: 15,
      },
    });
  }

  console.log("Seed complete");
  console.log({
    clinic: clinic.name,
    clinic2: clinic2.name,
    accounts: [
      "admin@clinic.local / password123",
      "employee@clinic.local / password123 (Sunrise desk)",
      "doctor@clinic.local / password123 (Dr. Mehta)",
      "employee.apollo@clinic.local / password123 (Apollo desk)",
      "doctor.apollo@clinic.local / password123 (Dr. Amit)",
      "patient@clinic.local / password123 (phone 9876543210)",
    ],
    doctorId: doctor.id,
    doctor2Id: doctor2.id,
    patientId: patient.id,
    employeeId: employee.id,
    adminId: admin.id,
  });
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
