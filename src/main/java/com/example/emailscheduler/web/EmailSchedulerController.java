package com.example.emailscheduler.web;

import java.time.ZonedDateTime;
import java.util.Date;
import java.util.UUID;

import javax.validation.Valid;

import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.emailscheduler.job.EmailJob;
import com.example.emailscheduler.payload.EmailRequest;
import com.example.emailscheduler.payload.EmailResponse;

@RestController
public class EmailSchedulerController {

	@Autowired
	private Scheduler scheduler;
	
	@PostMapping("/schedule/mail")
	public ResponseEntity<EmailResponse> scheduleMail(@Valid @RequestBody EmailRequest emailRequest){
		try {
			ZonedDateTime dateTime = ZonedDateTime.of(emailRequest.getDateTime(), emailRequest.getTimeZone());
			if(dateTime.isBefore(ZonedDateTime.now())) {
				EmailResponse emailResponse = new EmailResponse(false, "dateTime must be after current time.");
				return ResponseEntity.status(HttpStatus.BAD_REQUEST)
						.body(emailResponse);
			}
			
			JobDetail jobDetail = buildJobDetail(emailRequest);
			Trigger trigger = buildTrigger(jobDetail, dateTime);
			
			scheduler.scheduleJob(jobDetail,trigger);
			
			EmailResponse emailResponse = new EmailResponse(true,
					jobDetail.getKey().getName(), jobDetail.getKey().getGroup(),"Email scheduled successfully!");
			return ResponseEntity.ok(emailResponse);
		
		}catch(SchedulerException se) {
//			log.error("Error while scheduling email: ",se);
			EmailResponse emailResponse= new EmailResponse(false,"Errore while scheduling email. Try again later");
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
					.body(emailResponse);
		}
	}
	
	private JobDetail buildJobDetail(EmailRequest emailRequest) {
		JobDataMap jobDataMap = new JobDataMap();
		jobDataMap.put("email",emailRequest.getEmail());
		jobDataMap.put("subject", emailRequest.getSubject());
		jobDataMap.put("body", emailRequest.getBody());
		
		return JobBuilder.newJob(EmailJob.class) //if EmailJob is not 
				.withIdentity(UUID.randomUUID().toString(),"email-jobs") //group: "email-jobs"
				.withDescription("Send Email Job")
				.usingJobData(jobDataMap)
				.storeDurably()
				.build();
	}
	
	private Trigger buildTrigger(JobDetail jobDetail, ZonedDateTime startAt) {
		return TriggerBuilder.newTrigger()
				.forJob(jobDetail)
				.withIdentity(jobDetail.getKey().getName(),"email-triggers")
				.withDescription("Send Email Trigger")
				.startAt(Date.from(startAt.toInstant()))
				.withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow())
				.build();
	}
}
