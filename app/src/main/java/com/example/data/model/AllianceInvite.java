package com.example.data.model;

public class AllianceInvite {
    public String id;           // ID dokumenta poziva
    public String allianceId;
    public String allianceName;
    public String inviterUid;
    public String inviterName;

    public AllianceInvite() {}

    public AllianceInvite(String allianceId, String allianceName, String inviterUid, String inviterName) {
        this.allianceId = allianceId;
        this.allianceName = allianceName;
        this.inviterUid = inviterUid;
        this.inviterName = inviterName;
    }
}