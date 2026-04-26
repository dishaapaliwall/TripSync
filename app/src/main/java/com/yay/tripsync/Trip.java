package com.yay.tripsync;

import java.util.List;

public class Trip {
    private String name;
    private String location;
    private String startDate;
    private String endDate;
    private String status;
    private double budget;
    private double spent;
    private String imageUrl;
    private String tripCode;
    private String userId;
    private List<String> invitedEmails;
    private List<String> participants;
    private List<String> hiddenBy; // UIDs of users who hid this trip
 // UIDs of users who hid this trip

    public Trip() {}

    public Trip(String name, String location, String startDate, String endDate, String status, double budget, double spent, String imageUrl, String tripCode, String userId, List<String> invitedEmails, List<String> participants, List<String> hiddenBy) {
        this.name = name;
        this.location = location;
        this.startDate = startDate;
        this.endDate = endDate;
        this.status = status;
        this.budget = budget;
        this.spent = spent;
        this.imageUrl = imageUrl;
        this.tripCode = tripCode;
        this.userId = userId;
        this.invitedEmails = invitedEmails;
        this.participants = participants;
        this.hiddenBy = hiddenBy;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getStartDate() { return startDate; }
    public void setStartDate(String startDate) { this.startDate = startDate; }

    public String getEndDate() { return endDate; }
    public void setEndDate(String endDate) { this.endDate = endDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public double getBudget() { return budget; }
    public void setBudget(double budget) { this.budget = budget; }

    public double getSpent() { return spent; }
    public void setSpent(double spent) { this.spent = spent; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getTripCode() { return tripCode; }
    public void setTripCode(String tripCode) { this.tripCode = tripCode; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public List<String> getInvitedEmails() { return invitedEmails; }
    public void setInvitedEmails(List<String> invitedEmails) { this.invitedEmails = invitedEmails; }

    public List<String> getParticipants() { return participants; }
    public void setParticipants(List<String> participants) { this.participants = participants; }

    public List<String> getHiddenBy() { return hiddenBy; }
    public void setHiddenBy(List<String> hiddenBy) { this.hiddenBy = hiddenBy; }
}